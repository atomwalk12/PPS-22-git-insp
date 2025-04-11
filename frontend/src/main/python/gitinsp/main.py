import gradio as gr
import requests
import json
from dataclasses import dataclass
from requests.exceptions import RequestException
from json.decoder import JSONDecodeError

# Constants
TIMEOUT = 120000


class ChatInterface:
    def __init__(self, pretty_fmt=True):
        self.pretty_fmt = pretty_fmt


    def fix_misencoded_utf8(self, s):
        """Fix incorrectly encoded UTF-8 strings."""
        try:
            # Try to encode the string as latin1 and then decode as UTF-8
            # This fixes specific characters being terribly encoded
            return s.encode("latin1").decode("utf-8")
        except UnicodeDecodeError:
            # If decoding fails, simply return the string as is
            return s

    def chat(self, msg, history, index_name):
        """Stream chat responses from the server."""
        try:
            index_name = index_name if index_name != "" else None
            response = requests.get(
                "http://localhost:8080/chat",
                params={"msg": msg, "indexName": index_name},
                stream=True,
                headers={"Accept": "text/event-stream"},
            )

            is_first_message = True
            collected_message = ""

            for line in response.iter_lines(decode_unicode=True):
                if line:
                    # Ignore event prefixes like "event:" or empty lines
                    content = json.loads(line[5:])["text"]

                    if is_first_message and not content.startswith("<pre style"):
                        is_first_message = False
                        collected_message += "\n"

                    if len(content) == 0:
                        collected_message += "\n"
                    elif content.startswith("<think>"):
                        collected_message += "🤔\n"
                    elif content.startswith("</think>"):
                        collected_message += "\n🤔\n"
                    else:
                        collected_message += self.fix_misencoded_utf8(content)
                    yield collected_message

            return collected_message
        except RequestException as e:
            print(f"Request failed: {e}")
            yield "Error: Could not connect to chat service."
        except JSONDecodeError as e:
            print(f"JSON decode error: {e}")
            yield "Error: Could not decode chat service response."


    def fetch_link_content(self, link, format_type, extension):
        """Fetch content from a URL."""
        try:
            response = requests.get(
                "http://localhost:8080/fetch",
                params={"link": link, "format": format_type, "extension": extension},
            )
            return response.text
        except RequestException as e:
            return f"Error: {str(e)}"

    def generate_index(self, link, extensions):
        """Generate an index for a given repository link."""
        try:
            data = json.dumps({"indexName": link, "extensions": extensions})
            response = requests.post(
                "http://localhost:8080/generate", data={"data": data}, timeout=TIMEOUT
            )

            if response.status_code == 200:
                response_data = json.loads(response.text)
                index_name = response_data.get("indexName", "")
                result = response_data.get("result", "")
                current_choices = self.current_index_info.choices
                new_index = (index_name, index_name)

                # Check if the new index is already in the choices
                if new_index not in current_choices:
                    current_choices.append(new_index)

                return result, gr.Dropdown(
                    label="Current Index",
                    value=index_name,
                    interactive=True,
                    choices=current_choices,
                )
            else:
                return f"Error {response.status_code}: {response.text}", None
        except RequestException as e:
            return f"Error: {str(e)}"
        except Exception as e:
            return f"Error: {str(e)}"


    def load_index_from_server(self):
        """Fetch available indexes from the server."""
        try:
            response = requests.get("http://localhost:8080/list_indexes")
            if response.status_code == 200:
                response_data = json.loads(response.text)
                indexes = response_data.get("indexes", [])
                return [(idx, idx) if idx else ("No Index", "") for idx in indexes]
            else:
                return []
        except RequestException:
            return []

    def load_index(self):
        """Reload the list of indexes from the server."""
        index_value = self.current_index_info.value
        indexes = self.load_index_from_server()
        return gr.Dropdown(
            label="Current Index",
            value=index_value,
            interactive=True,
            choices=indexes,
        )

    def buildInterface(self):
        """Build and configure the Gradio interface."""

        with gr.Blocks(fill_height=True, css_paths="style.css") as self.interface:
            with gr.Tab("Chat", scale=55, elem_id="chat_tab"):
                # Add a dropdown to display the current index
                server_indexes = self.load_index_from_server()
                self.current_index_info = gr.Dropdown(
                    label="Current Index",
                    value="",
                    interactive=True,
                    choices=server_indexes,
                )

                load_index_button = gr.Button("Load Index")

                chat_interface = gr.ChatInterface(
                    fn=self.chat,
                    fill_height=True,
                    type="tuples",
                    additional_inputs=[self.current_index_info],
                )

            with gr.Tab("Link Viewer", scale=55):
                with gr.Row():
                    link_input = gr.Textbox(
                        label="Enter URL",
                        placeholder="Github repository or any other URL",
                        value="https://github.com/atomwalk12/PPS-22-git-insp",
                    )
                    options_dropdown = gr.Dropdown(
                        choices=["Github", "Plain Text", "Markdown", "Summary"],
                        label="Content Format",
                        value="Github",
                    )
                    extension = gr.Textbox(
                        label="Extension",
                        placeholder="Extensions separated by commas",
                        value="scala,md",
                    )

                fetch_button = gr.Button("Fetch Content")

                # Use a Column with fill_height to contain the TextArea
                with gr.Column(scale=100, elem_id="content_column"):
                    content_display = gr.TextArea(
                        label="Link Content",
                        interactive=True,
                        scale=100,
                        elem_id="content_display_area",
                    )

                generate_index_button = gr.Button("Generate Index")

                with gr.Row():
                    status_display = gr.Textbox(label="Status", interactive=False)

                # Set up event handlers
                generate_index_button.click(
                    fn=self.generate_index,
                    inputs=[link_input, extension],
                    outputs=[status_display, self.current_index_info],
                )

                fetch_button.click(
                    fn=self.fetch_link_content,
                    inputs=[link_input, options_dropdown, extension],
                    outputs=content_display,
                )

                load_index_button.click(
                    fn=self.load_index, inputs=[], outputs=[self.current_index_info]
                )

        return self.interface

    def launch(self):
        """Launch the interface."""
        self.interface.launch()


if __name__ == "__main__":
    app = ChatInterface(pretty_fmt=True)
    app.buildInterface()
    app.launch()
