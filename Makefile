.PHONY: docs
docs:
	lein codox
	cp docs/README.html docs/index.html
	echo ".doc, .public, .namespace .index {max-width: 1024px; font-size: 18px; margin: 0 auto;}" >> docs/css/default.css
