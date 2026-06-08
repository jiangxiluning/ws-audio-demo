from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class ProcessRequest(_message.Message):
    __slots__ = ("pcm_s16le", "gain_db", "duration_sec", "input_sample_rate", "output_sample_rate")
    PCM_S16LE_FIELD_NUMBER: _ClassVar[int]
    GAIN_DB_FIELD_NUMBER: _ClassVar[int]
    DURATION_SEC_FIELD_NUMBER: _ClassVar[int]
    INPUT_SAMPLE_RATE_FIELD_NUMBER: _ClassVar[int]
    OUTPUT_SAMPLE_RATE_FIELD_NUMBER: _ClassVar[int]
    pcm_s16le: bytes
    gain_db: float
    duration_sec: float
    input_sample_rate: int
    output_sample_rate: int
    def __init__(self, pcm_s16le: _Optional[bytes] = ..., gain_db: _Optional[float] = ..., duration_sec: _Optional[float] = ..., input_sample_rate: _Optional[int] = ..., output_sample_rate: _Optional[int] = ...) -> None: ...

class ProcessSegment(_message.Message):
    __slots__ = ("pcm_s16le", "segment_index", "segment_duration_sec", "is_last")
    PCM_S16LE_FIELD_NUMBER: _ClassVar[int]
    SEGMENT_INDEX_FIELD_NUMBER: _ClassVar[int]
    SEGMENT_DURATION_SEC_FIELD_NUMBER: _ClassVar[int]
    IS_LAST_FIELD_NUMBER: _ClassVar[int]
    pcm_s16le: bytes
    segment_index: int
    segment_duration_sec: float
    is_last: bool
    def __init__(self, pcm_s16le: _Optional[bytes] = ..., segment_index: _Optional[int] = ..., segment_duration_sec: _Optional[float] = ..., is_last: _Optional[bool] = ...) -> None: ...

class HealthCheckRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class HealthCheckResponse(_message.Message):
    __slots__ = ("status",)
    STATUS_FIELD_NUMBER: _ClassVar[int]
    status: str
    def __init__(self, status: _Optional[str] = ...) -> None: ...
