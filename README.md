# Developer Notes

The goal of this tool is to help developers to work with a typical corporate environment.

* No access to personal knowledge base building tools like obsidian, notion, etc.
* Have access create a personal git repository and store documents in it
* Need to work with multiple databases with different schemas
* Need a scripting language to gather data and display it in a nice way

## Features

* Store documents as markdown files
* Save the documents into a git repository
* Execute groovy scripts and render results in multiple formats
   - csv tables
   - html
   - text

### Groovy Scripting

To embed executable groovy code in a markdown file, use the following syntax:

To render the result as code block without any html formatting:
```groovy:code-block
def outputString = """
The following output will contain the angle brackets:

<h1>Hello World</h1>
"""
outputString
```

To render the result as a csv table:
```groovy:csv-table
def outputString = ""
for (int i = 0; i < 10; i++) {
    outputString += "${i},${i * i}\n"
}

outputString
```

To render the result as a csv table with header:
```groovy:csv-table-with-header
def outputString = ""
outputStrig += "n, n squared\n"
for (int i = 0; i < 10; i++) {
    outputString += "${i},${i * i}\n"
}

outputString
```

To render the result as html:
```groovy:html
def outputString = "<h1>Hello World</h1>"

outputString
```

To render the result as text:
```groovy:text
def outputString = "Hello World"

outputString
```

The code will be executed and the result will be rendered in the markdown file.

### Sql Scripting

To embed executable sql code in a markdown file, use the following syntax:

```sql(daasource1)
SELECT * FROM users
```

The datasource details are defined in a yaml file under '/config/datasource.yaml'.

```yaml
---
datasource1:
  url: "jdbc:h2:tcp://localhost:4000/./testdb"
  username: "sa"
  password: ""
  driverClassName: "org.h2.Driver"
datasource2:
  url: "jdbc:postgresql://localhost:5432/db2"
  username: "user2"
  password: "pass2"
  driverClassName: "org.postgresql.Driver"
```

## License
This project is licensed under MIT license.

See the [LICENSE](LICENSE) file for details
