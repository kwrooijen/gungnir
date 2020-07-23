.PHONY: docs
docs:
	lein codox
	cp docs/README.html docs/index.html
	echo ".doc, .public, .namespace .index {max-width: 924px; font-size: 16px; margin: 0 auto;}" >> docs/css/default.css
	echo ".markdown h2 {font-weight: bold;}" >> docs/css/default.css
	echo ".markdown h1 {font-weight: bold;}" >> docs/css/default.css
	echo ".footer-navigation {display: flex; justify-content: space-between; padding: 20px 0 30px;}" >> docs/css/default.css
