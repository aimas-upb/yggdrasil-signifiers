from http.server import HTTPServer, BaseHTTPRequestHandler
import json

class RequestHandler(BaseHTTPRequestHandler):
    def _set_headers(self):
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()

    def do_POST(self):
        # Get the content length from headers
        content_length = int(self.headers['Content-Length'])
        
        # Read the POST data
        post_data = self.rfile.read(content_length)
        
        try:
            # Try to parse as JSON
            json_data = json.loads(post_data.decode('utf-8'))
            print("Received POST request with data:")
            print(json.dumps(json_data, indent=2))
            
            # Send response
            self._set_headers()
            response = {
                "status": "success",
                "message": "Data received",
                "data": json_data
            }
            self.wfile.write(json.dumps(response).encode('utf-8'))
            
        except json.JSONDecodeError:
            # Handle non-JSON data
            print("Received non-JSON POST data:")
            print(post_data.decode('utf-8'))
            
            self._set_headers()
            response = {
                "status": "success",
                "message": "Raw data received",
                "data": post_data.decode('utf-8')
            }
            self.wfile.write(json.dumps(response).encode('utf-8'))

def run(server_class=HTTPServer, handler_class=RequestHandler, port=8081):
    server_address = ('localhost', port)
    httpd = server_class(server_address, handler_class)
    print(f"Starting server on localhost:{port}")
    httpd.serve_forever()

if __name__ == '__main__':
    run()
