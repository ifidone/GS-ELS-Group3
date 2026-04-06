FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
# Container images / Cloud Run typically expect 8080; local `python server.py` defaults to 8000 in code.
ENV MCP_PORT=8080

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8080

CMD ["python3", "server.py"]
