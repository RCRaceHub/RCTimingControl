#!/usr/bin/env python3
"""Fails when a Java file passed on the command line declares a top-level type without a Javadoc comment.

The Javadoc workflow runs it on the files a pull request adds or changes, so new and touched classes
carry their docs (CLAUDE.md rule 5) without the whole code base having to be documented first.
Usage: check-class-javadoc.py FILE...
"""
import re
import sys

TYPE = re.compile(r'^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static|strictfp)\s+)*'
                  r'(?:class|interface|enum|record|@interface)\s+\w+')


def first_type_has_javadoc(lines):
    """True when the first top-level type declaration is preceded by a /** ... */ comment, annotations aside."""
    last_comment = None  # 'javadoc' or 'other', for the closest comment above the declaration
    in_comment = False
    comment_kind = None
    for line in lines:
        s = line.strip()
        if in_comment:
            if '*/' in s:
                in_comment = False
                last_comment = comment_kind
            continue
        if s.startswith('/*'):
            comment_kind = 'javadoc' if s.startswith('/**') else 'other'
            if '*/' in s[2:]:
                last_comment = comment_kind
            else:
                in_comment = True
            continue
        if not s or s.startswith('//') or s.startswith('@') or s.startswith('package ') or s.startswith('import '):
            if s.startswith('package ') or s.startswith('import '):
                last_comment = None
            continue
        if TYPE.match(s):
            return last_comment == 'javadoc'
        if s.startswith(('(', ')', ',', '}')) or s.endswith(('(', ',')) or '=' in s:
            continue  # a multi-line annotation
        last_comment = None
    return True  # no type declaration (package-info, empty file)


def main(paths):
    bad = []
    for path in paths:
        if '/generated/' in path or not path.endswith('.java'):
            continue
        with open(path, encoding='utf-8') as f:
            if not first_type_has_javadoc(f.read().splitlines()):
                bad.append(path)
    for path in bad:
        print(f'::error file={path}::Add a Javadoc comment to the class (CLAUDE.md rule 5): what it is for, who calls it, thread and transaction notes')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
