FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# psycopg2-binary embarque ses libs ; pas de toolchain de compilation nécessaire.
# --timeout/--retries élevés : le réseau de build peut être lent (pandas ~12 Mo).
COPY requirements.txt .
RUN pip install --no-cache-dir --timeout 300 --retries 20 -r requirements.txt

COPY app ./app

EXPOSE 8100
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
