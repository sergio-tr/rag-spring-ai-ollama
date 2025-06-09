# classify_question.py

import tensorflow as tf
import sys
import numpy as np

# Cargar modelo
model = tf.keras.models.load_model("qa_model.keras")

# Cargar etiquetas
with open("query_type_labels.txt", "r") as f:
    class_names = [line.strip() for line in f.readlines()]

# Recibir pregunta por argumento
if len(sys.argv) < 2:
    print("CONTENT")  # fallback
    sys.exit(0)

pregunta = sys.argv[1]

# Predecir
pred = model.predict(tf.constant([pregunta]))[0]
idx = int(np.argmax(pred))
predicted_class = class_names[idx]

print(predicted_class)