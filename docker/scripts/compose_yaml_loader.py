"""YAML loader for Docker Compose files (supports merge tags like !reset)."""
from __future__ import annotations

try:
    import yaml
except ImportError as e:  # pragma: no cover
    raise ImportError("PyYAML is required: pip install pyyaml") from e


class ComposeYamlLoader(yaml.SafeLoader):
    """SafeLoader that understands Docker Compose merge tags (e.g. !reset)."""


def _compose_reset_constructor(loader: ComposeYamlLoader, node: yaml.Node):
    """Parse ``!reset`` merge overrides (Compose v2.24+)."""
    if isinstance(node, yaml.SequenceNode):
        return loader.construct_sequence(node)
    if isinstance(node, yaml.MappingNode):
        return loader.construct_mapping(node)
    if isinstance(node, yaml.ScalarNode):
        return loader.construct_scalar(node)
    return None


ComposeYamlLoader.add_constructor("!reset", _compose_reset_constructor)


def load_compose_yaml(text: str) -> dict:
    return yaml.load(text, Loader=ComposeYamlLoader) or {}
