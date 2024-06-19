from fastapi import FastAPI, UploadFile, File
from fastapi.responses import JSONResponse
from fastapi.responses import FileResponse
from deepface import DeepFace
import json
import os
import cv2
import shutil
import matplotlib.pyplot as plt
import numpy as np

app = FastAPI()

@app.post("/upload_video/")
async def upload_video(video: UploadFile = File(...)):
    video_path = os.path.join("video", video.filename)
    with open(video_path, "wb") as f:
        shutil.copyfileobj(video.file, f)
    extract_frames(video_path)
    return {"message": "Video uploaded and frames extracted"}

@app.get("/output/emotion_plot.png")
def get_emotion_plot():
    file_path = os.path.join("output", "emotion_plot.png")
    return FileResponse(file_path)

@app.get("/output/average_emotions.png")
def get_average_emotions():
    file_path = os.path.join("output", "average_emotions.png")
    return FileResponse(file_path)

def duplicate_json_file(second):
    json_path = os.path.join("jsons", f"average_emotions{second-5}.json")
    duplicate_path = os.path.join("jsons", f"average_emotions{second}.json")
    
    if os.path.exists(json_path):
        shutil.copyfile(json_path, duplicate_path)
        transform_emotions(duplicate_path)

def transform_emotions(json_file):
    with open(json_file, 'r') as file:
        data = json.load(file)
    
    emotions = ["злость", "раздражение", "страх", "счастье", "грусть", "удивление", "безучастность"]
    total = 0
    
    for emotion in emotions:
        total += data[emotion]
        data[emotion] = round(total, 2)
    
    data["безучастность"] = 100
    
    # Получение пути и имени файла
    file_path, file_name = os.path.split(json_file)
    
    # Создание нового пути для сохранения файла
    new_file_path = os.path.join("jsonsForChart")
    os.makedirs(new_file_path, exist_ok=True)
    
    # Создание нового пути и имени файла
    new_file_path = os.path.join(new_file_path, file_name)
    
    # Сохранение обновленного JSON файла
    with open(new_file_path, 'w') as new_file:
        json.dump(data, new_file, indent=4, ensure_ascii=False)
    
    return data

def rename_emotions_russian(data):
    emotions_mapping = {
        "angry": "злость",
        "disgust": "раздражение",
        "fear": "страх",
        "happy": "счастье",
        "sad": "грусть",
        "surprise": "удивление",
        "neutral": "безучастность"
    }
    
    for emotion in emotions_mapping:
        data[emotions_mapping[emotion]] = data.pop(emotion)
    
    return data


def EmotionAnalyze(frame_path, second):
    try:
        result_list = DeepFace.analyze(img_path=frame_path, actions=['emotion'])
        total_emotions = {}  # Словарь для хранения общих эмоций
        
        for result_dict in result_list:
            for k, v in result_dict.get('emotion').items():
                total_emotions[k] = total_emotions.get(k, 0) + v
        
        num_faces = len(result_list)
        average_emotions = {k: round(v / num_faces, 2) for k, v in total_emotions.items()}
        
        transformed_emotions = rename_emotions_russian(average_emotions)
        
        json_path = os.path.join("jsons", f"average_emotions{second}.json")
        with open(json_path, 'w') as file:
            json.dump(transformed_emotions, file, indent=4, ensure_ascii=False)

        transform_emotions(json_path)
        
        return result_list

    except Exception as _ex:
        print(_ex)
        duplicate_json_file(second)
        return _ex

def plot_emotions_from_jsons(json_folder, output_path):
    emotion_data = {}
    emotions = ["злость", "раздражение", "страх", "счастье", "грусть", "удивление", "безучастность"]
    
    # Инициализация словаря для каждой эмоции
    for emotion in emotions:
        emotion_data[emotion] = []

    # Считывание всех JSON файлов из папки
    for json_file in sorted(os.listdir(json_folder)):
        if json_file.endswith(".json"):
            second = int(json_file.split("average_emotions")[1].split(".json")[0])
            json_path = os.path.join(json_folder, json_file)
            
            with open(json_path, 'r') as file:
                data = json.load(file)
                for emotion in emotions:
                    emotion_data[emotion].append((second, data.get(emotion, 0)))

    # Построение графиков
    plt.figure(figsize=(12, 8))
    
    for i, emotion in enumerate(emotions):
        times, values = zip(*sorted(emotion_data[emotion]))
        plt.fill_between(times, values, label=emotion, zorder=len(emotions)-i)
    
    plt.xlabel('Время (секунды)')
    plt.ylabel('Значение эмоции')
    plt.title('Значения эмоций во времени')
    plt.legend()
    plt.grid(True)
    
    # Сохранение графика
    plt.savefig(output_path)
    # plt.show()

def plot_average_emotions(json_folder, output_path):
    emotion_totals = {}
    emotions = ["злость", "раздражение", "страх", "счастье", "грусть", "удивление", "безучастность"]
    
    # Инициализация суммарных значений эмоций
    for emotion in emotions:
        emotion_totals[emotion] = 0
    
    # Считывание всех JSON файлов из папки
    num_files = 0
    for json_file in sorted(os.listdir(json_folder)):
        if json_file.endswith(".json"):
            json_path = os.path.join(json_folder, json_file)
            
            with open(json_path, 'r') as file:
                data = json.load(file)
                for emotion in emotions:
                    emotion_totals[emotion] += data.get(emotion, 0)
            
            num_files += 1
    
    # Расчет средних значений эмоций
    for emotion in emotions:
        emotion_totals[emotion] /= num_files
    
    # Построение круговой диаграммы
    plt.figure(figsize=(8, 8))
    plt.pie(list(emotion_totals.values()), labels=list(emotion_totals.keys()), autopct='%1.1f%%')
    plt.title('Средние значения эмоций')
    
    # Сохранение диаграммы
    plt.savefig(output_path)
    # plt.show()

def extract_frames(video_path):
    cap = cv2.VideoCapture(video_path)
    count = 0
    frame_rate = 25
    while cap.isOpened():
        ret, frame = cap.read()

        if not ret:
            print("Finished extracting frames")
            break

        if count % (5*frame_rate) == 0:
            frame_path = f"frame_{count}.jpg"
            cv2.imwrite(frame_path, frame)
            second = count // frame_rate
            EmotionAnalyze(frame_path, second)
            os.remove(frame_path)

        count += 1
    json_folder = "jsonsForChart"
    output_path = os.path.join("output", "emotion_plot.png")
    plot_emotions_from_jsons(json_folder, output_path)
    json_folder = "jsons"
    output_path = os.path.join("output", "average_emotions.png")
    plot_average_emotions(json_folder, output_path)

video_path = os.path.join("video", "TestVideo.mp4")
extract_frames(video_path)
