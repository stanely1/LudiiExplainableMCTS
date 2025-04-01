#!/usr/bin/sh
mkdir -p .git/hooks
cp .githooks/* .git/hooks/
chmod +x .git/hooks/*

