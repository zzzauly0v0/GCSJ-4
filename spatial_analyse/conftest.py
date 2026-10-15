"""pytest 引导: 把仓库根目录加入 sys.path, 使 `spatial_analyse.*` 可导入。"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
