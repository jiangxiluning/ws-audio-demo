import os
from concurrent import futures

import grpc

from app.servicer import add_servicer

DEFAULT_PORT = 8090
MAX_MESSAGE_BYTES = 16 * 1024 * 1024


def serve() -> None:
    port = int(os.getenv("PCM_GRPC_PORT", str(DEFAULT_PORT)))
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=8),
        options=[
            ("grpc.max_send_message_length", MAX_MESSAGE_BYTES),
            ("grpc.max_receive_message_length", MAX_MESSAGE_BYTES),
        ],
    )
    add_servicer(server)
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"PCM gRPC server listening on :{port}", flush=True)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
