Set sh = CreateObject("WScript.Shell")
sh.CurrentDirectory = "C:\Users\lenovo\greenthumb\ai-service"
sh.Run "C:\Users\lenovo\miniconda3\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000", 0, False
