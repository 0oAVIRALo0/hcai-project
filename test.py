import requests

url = "http://localhost:5000/analyze"
audio_file_path = "/Users/aviralchauhan/College/Sem8/hcai/final_project/deepfake_detection/test_data/real.wav"  # Replace with your test file

# Send POST request
with open(audio_file_path, 'rb') as f:
    files = {'audio': (audio_file_path, f, 'audio/wav')}
    response = requests.post(url, files=files)

# Process response
if response.status_code == 200:
    result = response.json()
    print("Transcription:", result['transcription'])
    print(f"Deepfake: {result['deepfake']['result']} (Confidence: {result['deepfake']['confidence']:.2f})")
    print(f"Scam: {result['scam']['result']} (Confidence: {result['scam']['confidence']:.2f})")
else:
    print("Error:", response.json())