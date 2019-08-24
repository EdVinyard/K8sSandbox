#!/usr/bin/env python3
'''
usage: rendertemplate.py <template> <parameters>

    template    - a text file containing Python3 template string substitution 
                  targets (e.g., $COLOR)

    parameters  - a JSON file containing parameters that will be inserted into
                  the template

    Combines a template text file and parameters (from a JSON file) into
    final, rendered text.

Example:

    rendertemplate.py blog.yaml.template blog.params.json > blog.yaml

'''
import sys
import string
import json

USAGE = __doc__


def load_template(path):
    with open(path, 'r') as f:
        return f.read()


def load_parameters(path):
    with open(path, 'r') as f:
        return json.load(f)


def render(template, params):
    t = string.Template(template)
    return t.substitute(params)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(USAGE)
        exit(1)
    
    print(render(
        load_template(sys.argv[1]),
        load_parameters(sys.argv[2])))
