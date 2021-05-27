# -*- coding: utf-8 -*-
from datetime import datetime
from typing import Any
from typing import Generator
from typing import Optional
from typing import Tuple

import click
from dateutil.parser import parse

from pynessie import NessieClient
from pynessie.model import CommitMeta


def show_log(
    nessie: NessieClient,
    start: str,
    number: Optional[int] = -1,
    after: Optional[str] = None,
    before: Optional[str] = None,
    committer: Optional[str] = None,
    end: Optional[str] = None,
    limits: Tuple[click.Path] = None,
) -> Generator[CommitMeta, Any, None]:
    """Fetch and filter commit log.

    Note:
        limiting by path is not yet supported.
    """
    raw_log = nessie.get_log(start)

    def generator() -> Generator[CommitMeta, Any, None]:
        for i in raw_log:
            if end and i.hash_ == end:
                break
            yield i

    return generator()
