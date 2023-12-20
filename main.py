import os
import sys
import logging
try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)

load_dotenv()
INTENTS = discord.Intents.all()

class Bot(commands.Bot):
    async def on_ready(self):
        logging.info(f"Logged in as {self.user}")
        


bot = Bot(intents=INTENTS)

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    for file in os.listdir("games"):
        if file.endswith(".py"):
            bot.load_extension(f"games.{file[:-3]}")
            logging.info(f"Loaded game {file[:-3]}")
    bot.run(os.getenv("TOKEN"))