#!/bin/sh
set -eu
cd "$(dirname "$0")"
[ -x .venv/bin/python ] || python3 -m venv .venv
if [ ! -f .venv/requirements-installed.txt ] || ! cmp -s requirements.txt .venv/requirements-installed.txt; then
  .venv/bin/python -m pip install -r requirements.txt
  cp requirements.txt .venv/requirements-installed.txt
fi
exec .venv/bin/python -m streamlit run app.py --server.address 127.0.0.1
