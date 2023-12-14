from operator import is_
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
interview: list = []
LAST_MESSAGE_SENDER = 1
current_webhook: discord.Webhook | None = None
current_pp: int = 0
vote_enabled: bool = False
vote_cooldown: list[Timer] = []
votes: dict[str, dict[int, int]] = {}
current_vote: str | None = None

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

@bot.slash_command(name="notif", description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
async def notif(ctx: discord.ApplicationContext, role: discord.Role):
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.send_modal(Message([member for member in ctx.guild.members if role.id in [role.id for role in member.roles]], title="Quel message voulez vous envoyer ?")) # type: ignore

@bot.slash_command(name="interview", description="Permet d'interviewer un joueur dans le salon #annonces-village")
async def interview_command(ctx: discord.ApplicationContext, member: discord.Member):
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.guild.get_channel(GlobalChannel.ANNONCES_VILLAGE.value).set_permissions(member, send_messages=True) # type: ignore
    # On attends que le membre envoie un message
    await ctx.respond(f"Le channel a été ouvert pour {member.name}, vous pouvez lui poser vos questions !", ephemeral=True)
    interview.append(member.id)

@bot.slash_command(name="jour", description="Permet de passer au jour suivant")
async def day(ctx: discord.ApplicationContext):
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.default_role, send_messages=True) # type: ignore
    await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.default_role, send_messages=True) # type: ignore
    await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(ctx.guild.default_role, send_messages=False) # type: ignore
    await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(ctx.guild.default_role, send_messages=False) # type: ignore
    await ctx.respond("Le jour a été lancé !", ephemeral=True)

@bot.slash_command(name="nuit", description="Permet de passer à la nuit suivante")
async def night(ctx: discord.ApplicationContext):
    if not ctx.author.guild_permissions.administrator:  # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).send("----------") # type: ignore
    await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.default_role, send_messages=False) # type: ignore
    await ctx.guild.get_channel(GlobalChannel.VOTE.value).send("----------") # type: ignore
    await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.default_role, send_messages=False)  # type: ignore
    await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(ctx.guild.default_role, send_messages=True) # type: ignore
    await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(ctx.guild.default_role, send_messages=True) # type: ignore
    await ctx.respond("La nuit a été lancée !", ephemeral=True)


@bot.slash_command(name="mort", description="Permet de tuer un joueur")
async def death(ctx: discord.ApplicationContext, member: discord.Member):
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    # ON lui donne le role mort et on lui enleve le role vivant
    await member.add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
    await member.remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
    await ctx.respond(f"{member.name} a été tué !", ephemeral=True)


@bot.slash_command(name="start-vote", description="Permet de lancer un vote contre un joueur")
async def start_vote(ctx: discord.ApplicationContext, name: discord.Option(str, description="Le nom du vote", required=False)): # type: ignore
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)  
    global vote_enabled
    if vote_enabled:
        return await ctx.respond("Un vote est déjà en cours !", delete_after=10)
    vote_enabled = True
    global votes, current_vote
    if name is None:
        name = f"Vote {len(votes.keys()) + 1}"
    votes[name] = {}
    current_vote = name
    await ctx.respond(f"Le vote {name} a été lancé !", ephemeral=True)



@bot.slash_command(name="vote", description="Permet de voter contre un joueur")
async def vote(ctx: discord.ApplicationContext, member: discord.Member):
    if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
        return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
    if ctx.author.id == member.id:
        return await ctx.respond("Vous ne pouvez pas voter contre vous même !", delete_after=10)
    if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value in [role.id for role in member.roles]:
        return await ctx.respond("Vous ne pouvez pas voter contre un mort !", delete_after=10)
    if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value not in [role.id for role in member.roles]:
        return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !", delete_after=10)
    if ctx.author.id in vote_cooldown:
        return await ctx.respond("Vous êtes en cooldown !", delete_after=10)
    vote_cooldown.append(ctx.author.id) # type: ignore
    Timer(30, lambda: vote_cooldown.remove(ctx.author.id)).start() # type: ignore
    global votes, current_vote
    # On ajoute le vote sous la forme vote["nom_du_vote"][votant] = vote
    votes[current_vote][ctx.author.id] = member.id # type: ignore
    await ctx.respond(f"Vous avez voté contre {member.name} !", ephemeral=True)

@bot.slash_command(name="unvote", description="Permet d'annuler son vote")
async def unvote(ctx: discord.ApplicationContext):
    if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
        return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
    global votes
    if ctx.author.id not in votes[current_vote].keys(): # type: ignore
        return await ctx.respond("Vous n'avez pas voté !", delete_after=10)
    del votes[current_vote][ctx.author.id] # type: ignore
    await ctx.respond("Votre vote a été annulé !", ephemeral=True)


@bot.slash_command(name="vote-list", description="Permet de voir les votes en cours")
async def vote_list(ctx: discord.ApplicationContext):
    if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
        return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
    global votes, current_vote
    if current_vote is None:
        return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
    message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ {current_vote}\n━━━━━━━━━━━━━━━━━━\n"
    # On affiche vote : nombre de votes (voteurs)
    for vote in votes[current_vote].values(): # type: ignore
        message += f"{ctx.guild.get_member(vote).mention} : {list(votes[current_vote].values()).count(vote)} ({len([votant for votant in votes[current_vote].keys() if votes[current_vote][votant] == vote])})\n" # type: ignore
    await ctx.respond(discord.Embed(title="Votes", description=message), ephemeral=True)



@bot.slash_command(name="end-vote", description="Permet de terminer un vote")
async def end_vote(ctx: discord.ApplicationContext):
    if not ctx.author.guild_permissions.administrator: # type: ignore
        return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
    global vote_enabled, votes, current_vote
    if not vote_enabled:
        return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
    vote_enabled = False
    # On compte les votes
    votes_count = {}
    for vote in votes[current_vote].values(): # type: ignore
        if vote not in votes_count.keys():
            votes_count[vote] = 1
        else:
            votes_count[vote] += 1
    # On cherche le max
    max_votes = max(votes_count.values())
    # On cherche les joueurs qui ont le max
    max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
    # On regarde si il y a une égalité
    if len(max_votes_player) > 1:
        await ctx.respond("Il y a une égalité !", ephemeral=True)
        return
    # On le tue
    await ctx.guild.get_member(max_votes_player[0]).add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
    await ctx.guild.get_member(max_votes_player[0]).remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
    await ctx.respond(f"{ctx.guild.get_member(max_votes_player[0]).name} a été tué !", ephemeral=True) # type: ignore
    # On reset les votes
    current_vote = None
    global vote_cooldown
    vote_cooldown = []
    vote_enabled = False



    


@bot.event
async def on_message(message: discord.Message): 
    global LAST_MESSAGE_SENDER, current_webhook, current_pp
    guild = message.guild
    if guild is None:  # Vérifie si le message est envoyé en mp
        # On envoie le message avec un webhook dans le channel AdminChannel.MP
        try:
            webhook: discord.Webhook = await [webhook for webhook in await bot.get_channel(AdminChannel.MP.value).webhooks() if webhook.name == "MP"][0].edit("MP") # type: ignore

        except IndexError:
            webhook: discord.Webhook = await bot.get_channel(AdminChannel.MP.value).create_webhook(name="MP") # type: ignore
        await webhook.send(message.content, username=message.author.name, avatar_url=message.author.avatar.url) # type: ignore
        return
    if message.channel.id == GlobalChannel.ANNONCES_VILLAGE.value and message.author.id in interview:
        interview.remove(message.author.id)
        await message.channel.set_permissions(message.author, send_messages=False) # type: ignore
        return
    if message.channel.id == Channels.LOUP_CHAT.value and message.author.id not in [bot.user.id, Users.LUXIO.value] and not message.author.bot: # type: ignore
        if message.content.startswith("!") or message.content.startswith("/"):
            return
        content = message.content
        contents = []
        contents.append(content[:1990 if len(content) > 1990 else len(content)])
        if current_webhook is None:
            try:
                channel = bot.get_channel(Channels.PETITE_FILLE.value) 
                if channel is not None:
                    current_webhook = await [webhook for webhook in await channel.webhooks() if webhook.name == "🐺"][0].edit(name="🐺") # type: ignore
            except IndexError:
                current_webhook = await bot.get_channel(Channels.PETITE_FILLE.value).create_webhook(name="🐺") # type: ignore
        while len(content) > 1990:
            contents.append(content[:1990])
            content = content[1990:]
        if message.author.id == LAST_MESSAGE_SENDER:
            if len(contents) > 1:
                await current_webhook.send(contents[0], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                for part in contents[1:-1]:
                    await current_webhook.send(part, username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                await current_webhook.send(contents[-1], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
            else:
                await current_webhook.send(contents[0], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
        else:
            current_pp = 0 if current_pp == 1 else 1
            if len(contents) > 1:
                await current_webhook.send(contents[0], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                for part in contents[1:-1]:
                    await current_webhook.send(part, username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                await current_webhook.send(contents[-1], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
            else:
                await current_webhook.send(contents[0], username="🐺Anonyme" if current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
            LAST_MESSAGE_SENDER = message.author.id



if __name__ == "__main__":
    bot.run(os.getenv("TOKEN"))