@echo off
REM AI backend one-click launcher. Run from anywhere; cd to repo root first
REM so the spatial_analyse package is importable.
cd /d %~dp0..
uv run --project spatial_analyse uvicorn spatial_analyse.api_server:app --port 8062 --app-dir .
