import os
import contextlib
from pathlib import Path
from sys import argv

from pyrogram import Client
from pyrogram.types import InputMediaDocument

api_id = os.environ.get("APP_ID")
api_hash = os.environ.get("APP_HASH")
artifacts_path = Path("artifacts")
test_version = argv[3] == "test" if len(argv) > 2 else None


def find_apk(abi: str) -> Path:
    dirs = list(artifacts_path.glob("*"))
    for dir in dirs:
        if dir.is_dir():
            apks = list(dir.glob("*.apk"))
            for apk in apks:
                if abi in apk.name:
                    return apk

def get_thumb() -> str:
    return "TMessagesProj/src/main/" + "ic_launcher_nagram_block_round-playstore.png"

def get_commit_info():
    commit_id = os.environ.get("COMMIT_ID", "")[:7]
    commit_url = os.environ.get("COMMIT_URL", "")
    commit_message = os.environ.get("COMMIT_MESSAGE", "")
    return commit_id, commit_url, commit_message
 
def get_caption() -> str:
    commit_id, commit_url, commit_message = get_commit_info()
    pre = "Test version." if test_version else "Release version."
    caption = f"{pre}\n\n"
    caption += f"Commit Message:\n<blockquote expandable>{commit_message}</blockquote>\n\n"
    caption += f"See commit details [{commit_id}]({commit_url})"
    return caption

def get_document() -> list["InputMediaDocument"]:
    documents = []
    abis = ["arm64-v8a"]
    for abi in abis:
        if apk := find_apk(abi):
            documents.append(
                InputMediaDocument(
                    media=str(apk),
                    thumb=get_thumb(),
                )
            )
    documents[-1].caption = get_caption()
    print(documents)
    return documents

def retry(func):
    async def wrapper(*args, **kwargs):
        for _ in range(3):
            try:
                return await func(*args, **kwargs)
            except Exception as e:
                print(e)

    return wrapper

@retry
async def send_to_channel(client: "Client", cid: str):
    with contextlib.suppress(ValueError):
        cid = int(cid)
    await client.send_media_group(
        cid,
        media=get_document(),
    )

def get_client(bot_token: str):
    return Client(
        "helper_bot",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
    )

async def main():
    bot_token = argv[1]
    chat_id = argv[2]
    client = get_client(bot_token)
    await client.start()
    await send_to_channel(client, chat_id)
    await client.log_out()


if __name__ == "__main__":
    from asyncio import run

    run(main())
