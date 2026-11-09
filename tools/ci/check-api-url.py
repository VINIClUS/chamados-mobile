#!/usr/bin/env python3
"""Reject unusable release endpoints before building or loading signing keys."""
import os
import sys
from urllib.parse import urlsplit


def main():
    value = os.environ.get('CHAMADOS_API_BASE_URL', '')
    try:
        url = urlsplit(value)
        valid = (url.scheme == 'https' and bool(url.hostname) and url.port != 0
                 and not url.username and not url.password and not url.query and not url.fragment
                 and url.path.endswith('/') and not any(character.isspace() for character in value))
    except ValueError:
        valid = False
    if not valid:
        sys.exit('Invalid API URL: set CHAMADOS_API_BASE_URL to an HTTPS endpoint ending in /')


if __name__ == '__main__':
    main()
