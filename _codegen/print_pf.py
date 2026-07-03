"""print_pf.py <mcver> -- print the resource pack_format for a version (from compat_core)."""
import sys
import compat_core

print(compat_core.PACK_FORMATS[sys.argv[1]])
