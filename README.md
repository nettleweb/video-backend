# YTProxy
A simple YouTube proxy API server and stream extractor used by [NettleWeb Videos](https://nettleweb.com/videos).

## Quick Start
1. Ensure that [JDK-17](https://jdk.java.net/java-se-ri/17-MR1) is installed on your server.
2. Clone this repository onto your server with the `--recursive` flag.
```
git clone --recursive https://github.com/nettleweb/ytproxy.git
```
3. Build the JAR file.
```
./gradlew jar
```
4. Start the server.
```
java -Xms256m -Xmx1024m -jar ./build/libs/ytproxy-0.1.0.jar -host 127.0.0.1 -port 8080
```

## Using the API

### Getting search results
#### Request
```
GET http://127.0.0.1:8080/search?q=${query}&f=${filter}&p=${page}&s=${sort}
```
`query`: The search query string. (required)

`filter`: The search filter options.

`page`: The next page token.

`sort`: The sort filter options.

#### Response
A JSON-encoded string containing a list of matching videos and their information.

### Getting video streams
#### Request
```
GET http://127.0.0.1:8080/stream?u=${url}
```
`url`: The direct URL pointing to the video. (required)
#### Response
A JSON-encoded string containing the video's stream information.

### Getting trending videos
#### Request
```
GET http://127.0.0.1:8080/trending
```
#### Response
A JSON-encoded string containing a list of videos and their information.

## License
All code and files within this repository are licensed under the GPL-3.0 License. You are free to modify or redistribute this project under the terms stated in `LICENSE.md.`.
