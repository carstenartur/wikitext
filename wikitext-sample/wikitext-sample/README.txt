Carsten Hammer, 20.10.2013

I created a new sample how to generate a eclipse help plugin using tycho and mylyn wikitext. It takes two mediawiki categories as input and computes all the pages that are part of these categories and uses tycho to create a eclipse help plugin out of it.
The pages that belong to a category are computed using the mediawiki api and a stylesheet. It shows some serious limitations of the mylyn wikitext plugin. So if you try to create an eclipse help plugin from all wiki pages in the category mylyn you see what I mean. However for hand crafted wiki pages where you can adjust everything this might be a way to go. 
Just checkout the maven project and then run it like this:

mvn -Drcpversion=indigo package

or 

mvn -Drcpversion=kepler package

Here is the project:

https://github.com/carstenartur/wikitext/tree/master/wikitext-sample/wikitext-sample