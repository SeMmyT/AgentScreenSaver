"""mDNS service announcement for auto-discovery of bridge instances.

Registers a _ccrestatus._tcp.local. service so that screensaver clients
on the same LAN can discover this bridge without manual configuration.
"""

from __future__ import annotations

import logging
import socket

from zeroconf import ServiceInfo, Zeroconf

logger = logging.getLogger(__name__)

SERVICE_TYPE = "_ccrestatus._tcp.local."


def _get_local_ip() -> str:
    """Get the local IP address using the UDP socket trick.

    Connects a UDP socket to an external address (never sends data)
    to determine which local interface the OS would route through.
    Falls back to 127.0.0.1 if detection fails.
    """
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def register_service(
    port: int, instance_name: str | None = None
) -> tuple[Zeroconf, ServiceInfo]:
    """Register this bridge as an mDNS service.

    Args:
        port: The port the bridge server is listening on.
        instance_name: Human-readable name for this instance.
            Defaults to the system hostname.

    Returns:
        Tuple of (Zeroconf instance, ServiceInfo) for later cleanup.

    Raises:
        ValueError: If the detected local IP is 127.0.0.1 (loopback only),
            indicating no usable network interface for mDNS.
    """
    if instance_name is None:
        instance_name = socket.gethostname()

    local_ip = _get_local_ip()
    if local_ip == "127.0.0.1":
        raise ValueError(
            "Local IP resolved to 127.0.0.1 — no usable network interface "
            "for mDNS registration. Skipping."
        )

    # Service name format: "InstanceName._ccrestatus._tcp.local."
    service_name = f"{instance_name}.{SERVICE_TYPE}"

    info = ServiceInfo(
        type_=SERVICE_TYPE,
        name=service_name,
        addresses=[socket.inet_aton(local_ip)],
        port=port,
        properties={"instance": instance_name},
        server=f"{instance_name}.local.",
    )

    zc = Zeroconf()
    zc.register_service(info)
    logger.info(
        "Registered mDNS service: %s on %s:%d", service_name, local_ip, port
    )

    return zc, info


def unregister_service(zc: Zeroconf, info: ServiceInfo) -> None:
    """Unregister the mDNS service and close Zeroconf."""
    zc.unregister_service(info)
    zc.close()
    logger.info("Unregistered mDNS service: %s", info.name)
