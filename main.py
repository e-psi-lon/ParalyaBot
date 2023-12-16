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
        

class Message(discord.ui.Modal):
    def __init__(self, members: list[discord.Member], *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.members = members
        self.add_item(discord.ui.InputText(label="Long Input", style=discord.InputTextStyle.long))

    async def callback(self, interaction: discord.Interaction):
        message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGNotifications ¦ {self.children[0].value}\n━━━━━━━━━━━━━━━━━━"
        for member in self.members:
            if member.bot:
                continue
            await member.send(message)
        await interaction.response.send_message("Message envoyé !", ephemeral=True)


bot = Bot(intents=INTENTS)

if __name__ == "__main__":
    for file in os.listdir("games"):
        if file.endswith(".py"):
            bot.load_extension(f"games.{file[:-3]}")
            print(f"Loaded {file[:-3]}")
    bot.run(os.getenv("TOKEN"))