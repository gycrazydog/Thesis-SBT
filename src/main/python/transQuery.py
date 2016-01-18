#!/usr/bin/python
import xml.etree.ElementTree as etree

def parse(query):
	if(query.tag=="conjunct"):
		return parse(query[0])
	elif(query.tag=="star"):
		return "("+parse(query[0])+")*"
	elif(query.tag=="disj"):
		# ans = ""
		# print(type(query))
		# for subquery in query:
		# 	ans += "("+parse(subquery)+")|"
		ans = map(lambda x : "("+parse(x)+")" , query)
		return "|".join(ans)
	elif(query.tag=="concat"):
		ans = ""
		for subquery in query:
			ans += parse(subquery)
		return ans
	else:
		if(query.get("inverse")==None):
			return "["+query.text+"]"
		else:
			return "["+query.text+"-]"
tree = etree.parse("/home/crazydog/workspace/gmarkShare/use-cases/query-10m.xml")
queries = tree.getroot()
for query in queries:
	re = parse(query.iterfind('bodies/body/conjunct').next())
	print(re)