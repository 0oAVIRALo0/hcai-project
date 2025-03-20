from flask import Flask, request, jsonify
from werkzeug.utils import secure_filename
import os
import librosa
import numpy as np
from tensorflow.keras.models import load_model

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads/'
app.config['ALLOWED_EXTENSIONS'] = {'wav'}
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB limit

# Ensure upload directory exists
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

@app.route('/upload', methods=['POST'])
def upload_audio():
    # Check if file was uploaded
    if 'audio' not in request.files:
        return jsonify({'error': 'No audio file uploaded'}), 400
    
    file = request.files['audio']
    
    # Validate file
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400
    if not allowed_file(file.filename):
        return jsonify({'error': 'File type not allowed'}), 400

    # Save file securely
    filename = secure_filename(file.filename)
    save_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    file.save(save_path)
    
    return jsonify({'message': 'File uploaded successfully', 'filename': filename})

@app.route('/process', methods=['POST'])
def process_audio():
    # Get filename from request
    data = request.get_json()
    if not data or 'filename' not in data:
        return jsonify({'error': 'Filename required'}), 400
    
    filename = data['filename']
    file_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    
    # Validate file exists
    if not os.path.exists(file_path):
        return jsonify({'error': 'File not found'}), 404

    try:
        # Load model
        model = load_model('baseline_model/Baseline_Model.h5')
    except Exception as e:
        return jsonify({'error': f'Model loading failed: {str(e)}'}), 500

    try:
        # Process audio
        audio, sr = librosa.load(file_path, sr=16000)
        mfccs = librosa.feature.mfcc(y=audio, sr=sr, n_mfcc=40)
        
        # Pad/trim MFCC features
        max_length = 500
        if mfccs.shape[1] < max_length:
            mfccs = np.pad(mfccs, ((0, 0), (0, max_length - mfccs.shape[1])), mode='constant')
        else:
            mfccs = mfccs[:, :max_length]
            
        # Prepare input for model
        input_data = np.expand_dims(np.expand_dims(mfccs, axis=0), axis=-1)
        
        # Make prediction
        prediction = model.predict(input_data)
        confidence = float(prediction[0][0])
        label = "Fake" if confidence > 0.5 else "Real"

        # Delete the audio file from the upload directory
        os.remove(file_path)

        print("Prediction: " + str(confidence))
        print("Confidence: " + str(confidence))
        print("Label: " + str(label))
        
        return jsonify({
            'result': label,
            'confidence': confidence,
            'filename': filename
        })
    except Exception as e:
        return jsonify({'error': f'Processing failed: {str(e)}'}), 500

if __name__ == '__main__':
    app.run(debug=True)