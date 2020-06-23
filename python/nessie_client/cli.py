# -*- coding: utf-8 -*-
"""Console script for nessie_client."""
import os
import sys
from typing import Any

import click
import simplejson as json

from . import __version__
from .conf import build_config
from .nessie_client import NessieClient


def _print_version(ctx: Any, param: Any, value: Any) -> None:
    if not value or ctx.resilient_parsing:
        return
    click.echo(__version__)
    ctx.exit()


@click.group()
@click.option("--config", type=click.Path(exists=True, dir_okay=True, file_okay=False), help="Custom config file.")
@click.option("-e", "--endpoint", help="Endpoint if different from config file")
@click.option("-u", "--username", help="username if different from config file")
@click.option("--password", help="password if different from config file")
@click.option("--skip-verify", is_flag=True, help="skip verificatoin of ssl cert")
@click.option("--version", is_flag=True, callback=_print_version, expose_value=False, is_eager=True)
@click.pass_context
def cli(ctx: dict, config: str, endpoint: str, username: str, password: str, skip_verify: bool) -> None:
    """Nessie cli tool.

    Interact with Nessie branches and tables via the command line
    """
    if config:
        os.environ["NESSIE_CLIENTDIR"] = config
    config = build_config({"endpoint": endpoint, "username": username, "password": password, "verify": not skip_verify})
    nessie = NessieClient(config)
    ctx["nessie"] = nessie


@cli.command()
@click.pass_obj
def list_branches(args: dict) -> None:
    """List all known branches."""
    results = args["nessie"].list_branches()
    click.echo(json.dumps(results))


@cli.command()
@click.argument("branch", nargs=1, required=True)
@click.pass_obj
def show_branch(args: dict, branch: str) -> None:
    """Show a specific branch."""
    results = args["nessie"].get_branch(branch)
    click.echo(json.dumps(results))


@cli.command()
@click.argument("branch", nargs=1, required=True)
@click.option("--reason", "-r", type=str, help="reason for this action")
@click.pass_obj
def delete_branch(args: dict, branch: str, reason: str) -> None:
    """Delete a specific branch."""
    args["nessie"].delete_branch(branch, reason)
    click.echo()


@cli.command()
@click.argument("branch", nargs=1, required=True)
@click.option("--base-branch", "-b", type=str, help="branch to clone from")
@click.option("--reason", "-r", type=str, help="reason for this action")
@click.pass_obj
def create_branch(args: dict, branch: str, base_branch: str, reason: str) -> None:
    """Create a branch and optionally fork from base-branch."""
    args["nessie"].create_branch(branch, base_branch, reason)
    click.echo()


@cli.command()
@click.argument("from-branch", nargs=1, required=True)
@click.argument("to-branch", nargs=1, required=True)
@click.option("--force", "-f", default=False)
@click.option("--reason", "-r", type=str, help="reason for this action")
@click.pass_obj
def merge_branch(args: dict, from_branch: str, to_branch: str, force: bool, reason: str) -> None:
    """Merge FROM-BRANCH into TO-BRANCH. Optionally forced.

    When successful the TO-BRANCH will have all updates from FROM-BRANCH
    """
    args["nessie"].merge_branch(to_branch, from_branch, force, reason)
    click.echo()


@cli.command()
@click.argument("branch", nargs=1, required=True)
@click.option("--namespace", "-n", type=str, help="only include this namespace")
@click.pass_obj
def list_tables(args: dict, branch: str, namespace: str) -> None:
    """List tables from BRANCH."""
    tables = args["nessie"].list_tables(branch, namespace)
    click.echo(json.dumps(tables))


@cli.command()
@click.argument("branch", nargs=1, required=True)
@click.argument("table", nargs=-1, required=True)
@click.pass_obj
def show_table(args: dict, branch: str, *tables: str) -> None:
    """List tables from BRANCH."""
    tables = args["nessie"].get_tables(branch, tables)
    if len(tables) == 1:
        click.echo(json.dumps(tables[0]))
    else:
        click.echo(json.dumps(tables))


if __name__ == "__main__":
    sys.exit(cli())  # pragma: no cover
