"""
matplotlib 兼容层

背景：matplotlib 3.9 把 boxplot() 的 `labels` 参数改名为 `tick_labels`，
      旧名将在 3.11 移除。这里做一层兼容，避免以后升级后直接报错。

用法：
    from common.mpl_compat import boxplot
    boxplot(ax, data, labels=["a", "b"], patch_artist=True, widths=0.55)
"""
from __future__ import annotations

import matplotlib


def mpl_version() -> tuple[int, int]:
    try:
        parts = matplotlib.__version__.split(".")
        return int(parts[0]), int(parts[1])
    except Exception:
        return (3, 8)


def boxplot(ax, data, labels=None, **kwargs):
    """把 labels 自动映射到当前 matplotlib 版本认识的参数名"""
    if labels is not None:
        if mpl_version() >= (3, 9):
            kwargs["tick_labels"] = labels
        else:
            kwargs["labels"] = labels
    return ax.boxplot(data, **kwargs)
