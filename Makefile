.PHONY: docs
docs:
	lein codox
	cp docs/README.html docs/index.html
