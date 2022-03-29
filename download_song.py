from InteractWithTGBot import Client
import sys
import datetime

api_id = '13350158'
api_hash = '97e8c0bf00d9fd9c4dd9e36b907bb1d1'
database_encryption_key = 'keys/test_keys.txt'
phone = '+79112782398'

c = Client(api_id=api_id, api_hash=api_hash,
 database_encryption_key=database_encryption_key, phone=phone, verbose=True)

song_url = sys.argv[1]
#song_url = 'https://www.youtube.com/watch?v=Py0KMhyT6Bw'
bot_name = 'Youtube Audio Download'
c.send_message_sync(bot_name, song_url)
print('sent')
time = datetime.datetime.now()
answer = c.receive_answer(bot_name, 10)
print(datetime.datetime.now() - time)
print('received')
print(answer)
message_id = answer['message']['id']
from_chat_id = c.get_chat_id_by_bot_name(bot_name)
to_chat_id = -1001608346356
answer = c.forward_messages(to_chat_id=to_chat_id, from_chat_id=from_chat_id,
message_ids=[message_id], options=None, send_copy=True, remove_caption=True, only_preview=False)
print(answer['messages'][0]['id'])
print(answer)