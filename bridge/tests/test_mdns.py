"""Tests for mDNS service announcement."""

from __future__ import annotations

import socket
from unittest.mock import MagicMock, patch, call

import pytest

from bridge.mdns import (
    SERVICE_TYPE,
    _get_local_ip,
    register_service,
    unregister_service,
)


class TestRegisterService:
    """Tests for register_service()."""

    @patch("bridge.mdns.Zeroconf")
    @patch("bridge.mdns._get_local_ip", return_value="192.168.1.42")
    def test_creates_service_info_with_correct_type(
        self, mock_ip: MagicMock, mock_zc_cls: MagicMock
    ) -> None:
        """Verify ServiceInfo created with _ccrestatus._tcp.local. type and instance name."""
        mock_zc = mock_zc_cls.return_value

        zc, info = register_service(port=4001, instance_name="test-host")

        assert info.type == SERVICE_TYPE
        assert "test-host" in info.name
        assert info.port == 4001
        assert info.addresses == [socket.inet_aton("192.168.1.42")]
        mock_zc.register_service.assert_called_once_with(info)

    @patch("bridge.mdns.Zeroconf")
    @patch("bridge.mdns._get_local_ip", return_value="192.168.1.42")
    def test_uses_hostname_when_no_instance_name(
        self, mock_ip: MagicMock, mock_zc_cls: MagicMock
    ) -> None:
        """When instance_name is None, defaults to socket.gethostname()."""
        with patch("bridge.mdns.socket.gethostname", return_value="my-laptop"):
            zc, info = register_service(port=4001)

        assert "my-laptop" in info.name

    @patch("bridge.mdns._get_local_ip", return_value="127.0.0.1")
    def test_raises_on_loopback_ip(self, mock_ip: MagicMock) -> None:
        """If _get_local_ip() returns 127.0.0.1, skip registration with ValueError."""
        with pytest.raises(ValueError, match="127.0.0.1"):
            register_service(port=4001, instance_name="test")


class TestUnregisterService:
    """Tests for unregister_service()."""

    def test_unregister_and_close_called(self) -> None:
        """Verify unregister_service calls unregister and close on Zeroconf."""
        mock_zc = MagicMock()
        mock_info = MagicMock()
        mock_info.name = "test._ccrestatus._tcp.local."

        unregister_service(mock_zc, mock_info)

        mock_zc.unregister_service.assert_called_once_with(mock_info)
        mock_zc.close.assert_called_once()


class TestGetLocalIp:
    """Tests for _get_local_ip()."""

    @patch("bridge.mdns.socket.socket")
    def test_returns_detected_ip(self, mock_socket_cls: MagicMock) -> None:
        """Normal case: UDP socket trick returns a real IP."""
        mock_sock = MagicMock()
        mock_socket_cls.return_value.__enter__ = MagicMock(return_value=mock_sock)
        mock_socket_cls.return_value.__exit__ = MagicMock(return_value=False)
        mock_sock.getsockname.return_value = ("10.0.0.5", 0)

        result = _get_local_ip()
        assert result == "10.0.0.5"

    @patch("bridge.mdns.socket.socket")
    def test_falls_back_to_loopback_on_error(
        self, mock_socket_cls: MagicMock
    ) -> None:
        """When the UDP socket fails, falls back to 127.0.0.1."""
        mock_socket_cls.return_value.__enter__ = MagicMock(
            side_effect=OSError("No network")
        )
        mock_socket_cls.return_value.__exit__ = MagicMock(return_value=False)

        result = _get_local_ip()
        assert result == "127.0.0.1"
