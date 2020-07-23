.PHONY: docs
docs:
	lein codox
	cp docs/README.html docs/index.html
	echo ".doc, .public, .namespace .index {max-width: 924px; font-size: 16px; margin: 0 auto;}" >> docs/css/default.css
	echo ".markdown h2 {font-weight: bold;}" >> docs/css/default.css
