.PHONY: docs
docs:
	lein codox
	cp docs/README.html docs/index.html
	echo ".doc, .public, .namespace .index {max-width: 924px; font-size: 16px; margin: 0 auto;}" >> docs/css/default.css
	echo ".markdown h1,h2,h3,h4 {font-weight: bold;}" >> docs/css/default.css
	echo ".footer-navigation {display: flex; justify-content: space-between; padding: 20px 0 30px;}" >> docs/css/default.css
	echo ".markdown hr {margin-top: 30px; color: #eee;}" >> docs/css/default.css
