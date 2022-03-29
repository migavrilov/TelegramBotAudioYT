from telegram.client import Telegram, AuthorizationState
import asyncio
import os
from time import sleep

class Client:
    
    def __init__(self, api_id: str, api_hash: str, 
    database_encryption_key:str, phone: str, verbose: bool):
        self.verbose = verbose
        self.tg = Telegram(api_id, api_hash,
                database_encryption_key, phone)

        state = self.tg.login(blocking=False)
        if self.tg.authorization_state != AuthorizationState.READY:
            auth_data = input('Enter auth key that was sent to your telegram: ')
            self.tg.send_code(auth_data)

        state = self.tg.login(blocking=False)  
        if self.verbose:
            print(self.tg.authorization_state)
        
        self.tg.add_message_handler(self.main_message_handler)

    def get_chats(self) -> dict[any, any]:
        response = self.tg.get_chats()
        response.wait()
        return response.update

    def get_chat(self, chat_id: str) -> dict[any, any]:
        response = self.tg.get_chat(chat_id)
        response.wait()
        return response.update

    def get_chat_id_by_bot_name(self, bot_name: str) -> int:
        chats = self.get_chats()
        chat_ids = chats['chat_ids']
        for chat_id in chat_ids:
            chat = self.get_chat(chat_id)
            if chat['title'] == bot_name:
                return chat['id']
        
        raise Exception('Chat id was not found by bot_name ' + bot_name)

    def send_message_sync(self, bot_name: str, text: str) -> dict[any, any]:
        chat_id = self.get_chat_id_by_bot_name(bot_name)
        response = self.tg.send_message(chat_id, text)
        response.wait()
        return response.update

    def download_file(self, file_id: int, file_name: str) -> None:
        params = {'file_id': file_id, 'priority': 1, 'offset': 0, 
        'limit': 0, 'synchronous': True}
        response = self.tg.call_method('downloadFile', params=params)
        response.wait()
        if response.error_info != None:
            raise Exception(response.error_info)
        path = response.update['local']['path']
        os.replace(path, file_name)

    def receive_answer(self, bot_name: str, timeout: int) -> dict[any, any]: #timeout in seconds or 0
        timer = 0
        while timer < timeout or timeout == 0:
            if (self.update['message']['is_outgoing'] == False
            and self.update['message']['chat_id'] == self.get_chat_id_by_bot_name(bot_name)):
                return self.update
            
            sleep(0.1)
            timer += 0.1
        
        return {'@type': 'timeout'}
    
    def main_message_handler(self, update: dict[any, any]) -> None:
        self.update = update


    def forward_messages(self, to_chat_id: int, from_chat_id: int,
    message_ids: [int], options: dict[any, any], send_copy: bool,
    remove_caption: bool, only_preview: bool) -> dict[any, any]:
        params = {
            'chat_id': to_chat_id,
            'from_chat_id': from_chat_id,
            'message_ids': message_ids,
            'options': options,
            'send_copy': send_copy,
            'remove_caption': remove_caption,
            'only_preview': only_preview
        }
        response = self.tg.call_method('forwardMessages', params=params)
        response.wait()
        print(response.error_info)
        return response.update