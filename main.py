import os
import sys
try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)
from utils import *
from config import *


load_dotenv()
INTENTS = discord.Intents.all()


class Bot(commands.Bot):
    async def on_ready(self):
        print(f"Connecté en tant que {self.user}!")

class Message(discord.ui.Modal):
    def __init__(self, members, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.members = members
        self.add_item(discord.ui.InputText(label="Long Input", style=discord.InputTextStyle.long))

    async def callback(self, interaction: discord.Interaction):
        message = f"""━━━━━━━━━━━━━━━━━━
🐺 LGNotifications ¦ {self.children[0].value}
━━━━━━━━━━━━━━━━━━"""
        for member in self.members:
            if member.bot:
                continue
            await member.send(message)
        await interaction.response.send_message("Message envoyé !", ephemeral=True)


bot = Bot(intents=INTENTS)

@bot.slash_command(name="config", description="Configure le système d'envoi de messages anonymes")
async def config(ctx):
    if not ctx.author.guild_permissions.administrator: 
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    view = ConfigView(ctx)
    await ctx.respond(embed=EMBED_PAGE_DEFAULT, view=view)

@bot.slash_command(name="lg-notif", description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
async def notif(ctx, role: discord.Role):
    if not ctx.author.guild_permissions.administrator: 
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.send_modal(Message([member for member in ctx.guild.members if role.id in [role.id for role in member.roles]], title="Quel message voulez vous envoyer ?"))

@bot.event
async def on_message(message: discord.Message):
    try:
        config = get_anonymous(message.guild.id)
    except:
        return
    possible_link = get_list_with_user_channel(config, message.channel.id)
    bypassed_users = []
    for lists in possible_link:
        bypassed_users.extend(get_bypassed_users(config, lists[0], lists[1]))
    if message.author.bot or message.channel.id not in get_user_channels(get_anonymous(message.guild.id)) or message.content.startswith("/") or message.author.id in bypassed_users:
        return
    config = get_anonymous(message.guild.id)
    for user, anonymous, last_message_sender, _, escape_sequence in config:
        if user == message.channel.id:
            if escape_sequence is not None and message.content.startswith(escape_sequence):
                return
            if last_message_sender == message.author.id:
                if message.attachments:
                    files = [await attachment.to_file() for attachment in message.attachments]
                else:
                    files = []
                    channels = []
                for channel_id in get_anonymous_channels(get_anonymous(message.guild.id)):
                    for user, anonymous, _, _, _ in get_anonymous(message.guild.id):
                        if anonymous == channel_id and user == message.channel.id:
                            channels.append(bot.get_channel(channel_id))
                if len(message.content) > 1979:
                    texts = [message.content[i:i+1979] for i in range(0, len(message.content), 1979)]
                    for channel in channels:
                        for index, text in enumerate(texts):
                            if index == len(texts)-1:
                                await channel.send(text, files=files)
                            else:
                                await channel.send(text)
                else:
                    for channel in channels:
                        await channel.send(message.content, files=files)

                return
    if message.attachments:
        files = [await attachment.to_file() for attachment in message.attachments]
    else:
        files = []
    channels = []
    for channel_id in get_anonymous_channels(get_anonymous(message.guild.id)):
        for user, anonymous, _, _, _ in get_anonymous(message.guild.id):
            if anonymous == channel_id and user == message.channel.id:
                channels.append(bot.get_channel(channel_id))
    if len(message.content) > 1979:
        texts = [message.content[i:i+1979] for i in range(0, len(message.content), 1979)]
        for channel in channels:
            for index, text in enumerate(texts):
                if index == 0:
                    await channel.send(f"**Message anonyme**\n\n{text}", files=files)
                elif index == len(texts)-1:
                    await channel.send(text, files=files)
                else:
                    await channel.send(text)
    else:
        for channel in channels:
            await channel.send(f"**Message anonyme**\n\n{message.content}", files=files)
    config = get_anonymous(message.guild.id)
    for user, anonymous, last_message_sender, _, _ in config:
        if user == message.channel.id and anonymous == channels[0].id:
            config[get_list_index(config, user, anonymous)][2] = message.author.id
    edit_anonymous(config, message.guild.id)
    
def start(instance):
    instance.run(str(os.getenv('TOKEN')))


if __name__ == "__main__":
    start(bot)
