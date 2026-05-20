"""
Shared fixtures for classifier-service tests.
"""
import io
import os

import pandas as pd
import pytest

os.environ.setdefault("MODELS_DIR", "models")

_COLLECTED_COUNT = 0
_PASSED_NODEIDS: set[str] = set()
_FAILED_NODEIDS: set[str] = set()
_SKIPPED_NODEIDS: set[str] = set()
_FALSE_GREEN_GUARD_FAILURE = ""


def _false_green_guard_enabled() -> bool:
    return os.environ.get("CLASSIFIER_PYTEST_FALSE_GREEN_GUARD", "1").strip().lower() not in (
        "0",
        "false",
        "no",
        "off",
    )


def pytest_configure(config: pytest.Config) -> None:
    global _COLLECTED_COUNT, _FALSE_GREEN_GUARD_FAILURE
    del config
    _COLLECTED_COUNT = 0
    _FALSE_GREEN_GUARD_FAILURE = ""
    _PASSED_NODEIDS.clear()
    _FAILED_NODEIDS.clear()
    _SKIPPED_NODEIDS.clear()


def pytest_collection_finish(session: pytest.Session) -> None:
    global _COLLECTED_COUNT
    _COLLECTED_COUNT = len(session.items)


def pytest_runtest_logreport(report: pytest.TestReport) -> None:
    if report.outcome == "passed" and report.when == "call":
        _PASSED_NODEIDS.add(report.nodeid)
    elif report.outcome == "failed":
        _FAILED_NODEIDS.add(report.nodeid)
    elif report.outcome == "skipped":
        _SKIPPED_NODEIDS.add(report.nodeid)


def pytest_sessionfinish(session: pytest.Session, exitstatus: int) -> None:
    global _FALSE_GREEN_GUARD_FAILURE
    del exitstatus
    if not _false_green_guard_enabled():
        return
    if _COLLECTED_COUNT <= 0:
        _FALSE_GREEN_GUARD_FAILURE = "classifier pytest false-green guard failed: zero tests collected."
        session.exitstatus = pytest.ExitCode.TESTS_FAILED
        return
    if len(_SKIPPED_NODEIDS) >= _COLLECTED_COUNT and not _PASSED_NODEIDS and not _FAILED_NODEIDS:
        _FALSE_GREEN_GUARD_FAILURE = (
            "classifier pytest false-green guard failed: all collected tests were skipped "
            f"({_COLLECTED_COUNT}/{_COLLECTED_COUNT})."
        )
        session.exitstatus = pytest.ExitCode.TESTS_FAILED


def pytest_terminal_summary(terminalreporter: pytest.TerminalReporter) -> None:
    if _FALSE_GREEN_GUARD_FAILURE:
        terminalreporter.section("classifier false-green guard")
        terminalreporter.write_line(_FALSE_GREEN_GUARD_FAILURE)


@pytest.fixture
def client():
    """HTTP client for the FastAPI app."""
    from uvicorn_entry import app
    from fastapi.testclient import TestClient
    return TestClient(app)


@pytest.fixture
def minimal_dataset_excel():
    """Minimal Excel with Question and QueryType columns for POST /train."""
    df = pd.DataFrame({
        "Question": [
            "How many documents?",
            "How many actas?",
            "Summarize the meeting",
            "Summarize the minutes",
            "How many meetings?",
        ],
        "QueryType": [
            "COUNT_DOCUMENTS",
            "COUNT_DOCUMENTS",
            "SUMMARIZE_MEETING",
            "SUMMARIZE_MEETING",
            "COUNT_DOCUMENTS",
        ],
    })
    buf = io.BytesIO()
    df.to_excel(buf, index=False, engine="openpyxl")
    buf.seek(0)
    return buf
