from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
import os
import librosa
import numpy as np
import torch
import whisper
from tensorflow.keras.models import load_model
from transformers import BertTokenizer, BertForSequenceClassification
from pydub import AudioSegment
import warnings
warnings.filterwarnings("ignore")

app = Flask(__name__)

# Configuration
app.config['UPLOAD_FOLDER'] = 'uploads/'
app.config['ALLOWED_EXTENSIONS'] = {'wav', 'mp3', 'ogg', 'm4a'}
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB

# Ensure upload directory exists
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

def create_mel_spectrogram(file_path):
    audio_data, sample_rate = librosa.load(file_path)
    mel_spectrogram = librosa.feature.melspectrogram(y=audio_data, sr=sample_rate)
    mel_decibel_spectrogram = librosa.power_to_db(mel_spectrogram, ref=np.max)
    return mel_decibel_spectrogram

def preprocess_audio_for_prediction(file_path):
    mel_spectrogram = create_mel_spectrogram(file_path)
    mel_spectrogram_resized = np.resize(mel_spectrogram, (128, 87))
    mel_spectrogram_resized = np.expand_dims(mel_spectrogram_resized, axis=-1)
    mel_spectrogram_resized = np.expand_dims(mel_spectrogram_resized, axis=0)
    return mel_spectrogram_resized

# Load models at startup
try:
    whisper_model = whisper.load_model("base")
    audio_model = load_model('/Users/aviralchauhan/College/Sem8/hcai/final_project/hcai_final_project_scam_detection/deepfake_detection/models/Deepfake Audio Detector Model.h5')
    text_tokenizer = BertTokenizer.from_pretrained('/Users/aviralchauhan/College/Sem8/hcai/final_project/hcai_final_project_scam_detection/scam_genuine_nlp/Fraud Detection BERT')
    text_model = BertForSequenceClassification.from_pretrained('/Users/aviralchauhan/College/Sem8/hcai/final_project/hcai_final_project_scam_detection/scam_genuine_nlp/Fraud Detection BERT')
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    text_model.to(device)
    text_model.eval()
except Exception as e:
    print(f"Error loading models: {e}")
    raise RuntimeError("Failed to initialize models") from e

@app.route('/analyze', methods=['POST'])
def analyze_audio():
    if 'audio' not in request.files:
        return jsonify({'error': 'No audio file provided'}), 400

    file = request.files['audio']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400
    if not allowed_file(file.filename):
        return jsonify({'error': 'File type not allowed'}), 400

    original_filename = secure_filename(file.filename)
    original_ext = original_filename.rsplit('.', 1)[1].lower()
    temp_path = os.path.join(app.config['UPLOAD_FOLDER'], original_filename)

    try:
        file.save(temp_path)

        if original_ext != 'wav':
            audio = AudioSegment.from_file(temp_path)
            wav_filename = f"{os.path.splitext(original_filename)[0]}.wav"
            wav_path = os.path.join(app.config['UPLOAD_FOLDER'], wav_filename)
            audio.export(wav_path, format='wav')
            os.remove(temp_path)
            save_path = wav_path
        else:
            save_path = temp_path

    except Exception as e:
        return jsonify({'error': f'File processing failed: {str(e)}'}), 500

    response_data = {
        'transcription': '',
        'deepfake': {'result': '', 'confidence': 0.0},
        'scam': {'result': '', 'confidence': 0.0},
        'error': None
    }

    try:
        # Step 1: Transcription
        try:
            result = whisper_model.transcribe(save_path)
            transcription = result['text']
            response_data['transcription'] = transcription
        except Exception as e:
            response_data['error'] = f'Transcription failed: {str(e)}'
            raise

        # Step 2: Deepfake Detection
        try:
            mel_spectrogram_resized = preprocess_audio_for_prediction(save_path)
            prediction = audio_model.predict(mel_spectrogram_resized)
            confidence = float(prediction[0][0])
            result = 'Fake' if confidence > 0.5 else 'Real'
            response_data['deepfake']['result'] = result
            response_data['deepfake']['confidence'] = confidence
        except Exception as e:
            response_data['error'] = f'Deepfake analysis failed: {str(e)}'
            raise

        # Step 3: Scam Text Detection
        if transcription.strip():
            try:
                inputs = text_tokenizer(
                    transcription,
                    truncation=True,
                    padding='max_length',
                    max_length=256,
                    return_tensors='pt'
                )
                inputs = {k: v.to(device) for k, v in inputs.items()}
                with torch.no_grad():
                    outputs = text_model(**inputs)
                logits = outputs.logits
                probs = torch.nn.functional.softmax(logits, dim=1)
                scam_prob = probs[0][1].item()
                scam_result = 'Scam' if scam_prob > 0.5 else 'Not Scam'
                response_data['scam']['result'] = scam_result
                response_data['scam']['confidence'] = scam_prob
            except Exception as e:
                response_data['error'] = f'Scam analysis failed: {str(e)}'
                raise
        else:
            response_data['scam']['result'] = 'No text to analyze'

    except Exception as e:
        if not response_data['error']:
            response_data['error'] = f'Processing failed: {str(e)}'
        app.logger.error(f"Error processing file: {str(e)}")

    finally:
        try:
            os.remove(save_path)
        except Exception as e:
            app.logger.error(f"Failed to delete file: {str(e)}")

    if response_data['error']:
        return jsonify({'error': response_data['error']}), 500
    else:
        return jsonify(response_data)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
