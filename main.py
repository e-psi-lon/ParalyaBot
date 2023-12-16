import os
import sys
try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)
from enums import *
from threading import Timer


load_dotenv()
INTENTS = discord.Intents.all()

class Bot(commands.Bot):
    async def on_ready(self):
        print(f"Connecté en tant que {self.user}!")
        


bot = Bot(intents=INTENTS)

if __name__ == "__main__":
    for file in os.listdir("games"):
        if file.endswith(".py"):
            bot.load_extension(f"games.{file[:-3]}")
            print(f"Loaded {file[:-3]}")
    bot.run(os.getenv("TOKEN"))