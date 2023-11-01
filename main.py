import os
import sys
try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)


load_dotenv()
INTENTS = discord.Intents.all()
interview = []
ANNONCES_VILLAGE = 1167436729767174275
LOUP_CHAT = 731835704782487592
PETITE_FILLE = 711987258340671590
LAST_MESSAGE_SENDER = 1


class Bot(commands.Bot):
    async def on_ready(self):
        print(f"Connecté en tant que {self.user}!")

class Message(discord.ui.Modal):
    def __init__(self, members, *args, **kwargs):
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

@bot.slash_command(name="lg-notif", description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
async def notif(ctx, role: discord.Role):
    if not ctx.author.guild_permissions.administrator: 
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.send_modal(Message([member for member in ctx.guild.members if role.id in [role.id for role in member.roles]], title="Quel message voulez vous envoyer ?"))

@bot.slash_command(name="lg-interview", description="Permet d'interviewer un joueur dans le salon #annonces-village")
async def interview_command(ctx, member: discord.Member):
    if not ctx.author.guild_permissions.administrator: 
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.guild.get_channel(ANNONCES_VILLAGE).set_permissions(member, send_messages=True)
    # On attends que le membre envoie un message
    await ctx.respond(f"Le channel a été ouvert pour {member.name}, vous pouvez lui poser vos questions !", ephemeral=True)
    interview.append(member.id)


@bot.event
async def on_message(message: discord.Message): 
    global LAST_MESSAGE_SENDER
    guild = message.guild
    if guild is None:
        return
    if message.channel.id == ANNONCES_VILLAGE and message.author.id in interview:
        interview.remove(message.author.id)
        await message.channel.set_permissions(message.author, send_messages=False) # type: ignore
        return
    if message.channel.id == LOUP_CHAT and message.author.id not in [bot.user.id, 265178325381677059] and not message.author.bot: # type: ignore
        if message.content.startswith("!") or message.content.startswith("/"):
            return
        content = message.content
        contents = []
        contents.append(content[:1990 if len(content) > 1990 else len(content)])
        while len(content) > 1990:
            contents.append(content[:1990])
            content = content[1990:]
        if message.author.id == LAST_MESSAGE_SENDER:
            if len(contents) > 1:
                await guild.get_channel(PETITE_FILLE).send(contents[0]) # type: ignore
                for part in contents[1:-1]:
                    await guild.get_channel(PETITE_FILLE).send(part) # type: ignore
                await guild.get_channel(PETITE_FILLE).send(contents[-1], files=message.attachments) # type: ignore
            else:
                await guild.get_channel(PETITE_FILLE).send(contents[0], files=message.attachments) # type: ignore
        else:
            if len(contents) > 1:
                await guild.get_channel(PETITE_FILLE).send(f"🐺 : {contents[0]}") # type: ignore
                for part in contents[1:-1]:
                    await message.guild.get_channel(PETITE_FILLE).send(part) # type: ignore
                await guild.get_channel(PETITE_FILLE).send(contents[-1], files=message.attachments) # type: ignore
            else:
                await guild.get_channel(PETITE_FILLE).send(f"🐺 : {contents[0]}", files=message.attachments) # type: ignore
            LAST_MESSAGE_SENDER = message.author.id


if __name__ == "__main__":
    bot.run(os.getenv("TOKEN"))