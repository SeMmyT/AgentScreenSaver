"""Shared test fixtures and configuration."""

from __future__ import annotations

from unittest.mock import patch

import pytest


@pytest.fixture(autouse=True)
def _mock_mdns_for_server_tests(request: pytest.FixtureRequest):
    """Auto-mock mDNS registration in server tests to avoid real Zeroconf calls.

    Only applies when bridge.server is involved (test_server.py).
    mDNS unit tests (test_mdns.py) manage their own mocks.
    """
    if "test_mdns" in request.node.nodeid:
        yield
        return

    with patch("bridge.server.register_service") as mock_reg, \
         patch("bridge.server.unregister_service") as mock_unreg:
        # Make register_service return a mock tuple so on_startup succeeds
        from unittest.mock import MagicMock
        mock_reg.return_value = (MagicMock(), MagicMock())
        yield
