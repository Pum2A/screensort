"""Packaged desktop entry point: bind an OS-assigned loopback port, not PATH tools."""

import argparse
import json
import os
import socket
from pathlib import Path

import uvicorn

from screensort_analysis.main import create_app


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state-file", required=True, type=Path)
    parser.add_argument("--model-cache", required=True, type=Path)
    args = parser.parse_args()
    os.environ["SCREENSORT_MODEL_CACHE"] = str(args.model_cache.resolve())
    app = create_app()
    # Keep this socket open so another process cannot steal its selected port.
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen(16)
        listener.setblocking(False)
        args.state_file.write_text(
            json.dumps({"port": listener.getsockname()[1], "pid": os.getpid()}),
            encoding="utf-8",
        )
        config = uvicorn.Config(app, access_log=False, log_level="warning", workers=1)
        server = uvicorn.Server(config)
        app.state.server = server
        server.run(sockets=[listener])


if __name__ == "__main__":
    main()
