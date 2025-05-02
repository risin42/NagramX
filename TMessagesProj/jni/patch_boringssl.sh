#!/bin/bash

set -e

patch -d boringssl -p1 < patches/boringssl/0001-add-aes-ige-mode.patch
