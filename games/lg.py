from discord.ext import commands
import discord
from threading import Timer
from enums import *

async def get_webhook(bot, channel, name) -> discord.Webhook:
    try:
        webhook: discord.Webhook = await [webhook for webhook in await bot.get_channel(channel).webhooks() if webhook.name == name][0].edit(name=name) # type: ignore
    except IndexError:
        webhook: discord.Webhook = await bot.get_channel(channel).create_webhook(name=name) # type: ignore
    return webhook


class Message(discord.ui.Modal):
    def __init__(self, members: list[discord.Member], *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.members = members
        self.add_item(discord.ui.InputText(label="Message à envoyer", style=discord.InputTextStyle.long))

    async def callback(self, interaction: discord.Interaction):
        message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGNotifications ¦ {self.children[0].value}\n━━━━━━━━━━━━━━━━━━"
        for member in self.members:
            if member.bot:
                continue
            await member.send(message)
        await interaction.response.send_message("Message envoyé !", ephemeral=True)


class LG(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.interview: list = []
        self.LAST_MESSAGE_SENDER = 1
        self.current_pp: int = 0
        self.vote_cooldown: list[int] = []
        self.village_votes: dict[str, dict[int, int] | bool | list[int] | int] = {"is_vote": False, "votes": {}, "choices": [], "corbeau": 0}
        self.loup_votes: dict[str, dict[int, int] | bool | list[int]] = {"is_vote": False, "votes": {}, "choices": []}
        self.time = "nuit"

    lg = discord.SlashCommandGroup(name="lg", description="Commandes pour le Loup-Garou")
    
    @lg.command(name="notif", description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
    async def notif(self, ctx: discord.ApplicationContext, role: discord.Role):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        await ctx.send_modal(Message([member for member in ctx.guild.members if role.id in [role.id for role in member.roles]], title="Quel message voulez vous envoyer ?")) # type: ignore

    @lg.command(name="interview", description="Permet d'interviewer un joueur dans le salon #annonces-village")
    async def interview_command(self, ctx: discord.ApplicationContext, member: discord.Member):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        await ctx.guild.get_channel(GlobalChannel.ANNONCES_VILLAGE.value).set_permissions(member, send_messages=True) # type: ignore
        # On attends que le membre envoie un message
        await ctx.respond(f"Le channel a été ouvert pour {member.name}, vous pouvez lui poser vos questions !", ephemeral=True)
        self.interview.append(member.id)

    @lg.command(name="jour", description="Permet de passer au jour suivant")
    async def day(self, ctx: discord.ApplicationContext):
        await ctx.response.defer()
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        if self.time == "jour":
            return await ctx.respond("Vous ne pouvez pas lancer un jour alors qu'un jour est déjà en cours", delete_after=10)
        self.village_votes["is_vote"] = True
        self.village_votes["votes"] = {}
        self.loup_votes["is_vote"] = False
        # On tue le joueur le plus voté par les loups
        if len(self.loup_votes["votes"].keys()) > 0: # type: ignore
            votes_count = {}
            for vote in self.loup_votes["votes"].values(): # type: ignore
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
                await ctx.guild.get_channel(Channels.LOUP_VOTE.value).send("Il y a une égalité, décidez vous sur qui tuer : " + ", ".join([ctx.guild.get_member(player).mention for player in max_votes_player])) # type: ignore
                self.loup_votes["votes"] = {}
                self.loup_votes["choices"] = max_votes_player
                self.loup_votes["is_vote"] = True
                return await ctx.respond("Un second vote est donc lancé !", ephemeral=True)
            # On le tue
            await ctx.guild.get_member(max_votes_player).add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
            await ctx.guild.get_member(max_votes_player).remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
            await ctx.respond(f"{ctx.guild.get_member(max_votes_player).name} a été tué !", ephemeral=True) # type: ignore
        self.loup_votes["choices"] = []
        self.time = "jour"
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=True, view_channel=True, reason="Passage au jour") # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=True, view_channel=True, reason="Passage au jour") # type: ignore
        await ctx.guild.get_channel(GlobalChannel.SUJET.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=True, view_channel=True, reason="Passage au jour") # type: ignore
        for thread in ctx.guild.get_channel(GlobalChannel.SUJET.value).threads: # type: ignore
            await thread.edit(locked=False, reason="Passage au jour")
        for user in ctx.guild.members: # type: ignore
            # Si l'utilisateur a accès a LOUP_CHAT et à LOUP_VOTE on lui redonne la permission d'écrire, sinon on passe
            if user in [member for member in ctx.guild.get_channel(Channels.LOUP_CHAT.value).members] and Roles.LG_VIVANT.value in [role.id for role in user.roles]: # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(user, send_messages=False, view_channel=True, reason="Passage au jour") # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(user, send_messages=False, view_channel=True, reason="Passage au jour") # type: ignore
        await ctx.respond("Le jour a été lancé !", ephemeral=True)

    @lg.command(name="nuit", description="Permet de passer à la nuit suivante")
    async def night(self, ctx: discord.ApplicationContext):
        await ctx.response.defer()
        if not ctx.author.guild_permissions.administrator:  # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        if self.time == "nuit":
            return await ctx.respond("Vous ne pouvez pas lancer une nuit alors qu'une nuit est déjà en cours", delete_after=10)
        # On compte les votes
        self.loup_votes["is_vote"] = True
        self.loup_votes["votes"] = {}
        self.village_votes["is_vote"] = False
        if len(self.village_votes["votes"].keys()) > 0: # type: ignore
            votes_count = {}
            for vote in self.village_votes["votes"].values(): # type: ignore
                if vote not in votes_count.keys():
                    votes_count[vote] = 1
                else:
                    votes_count[vote] += 1
            # On cherche le max
            if self.village_votes["corbeau"] != 0:
                if self.village_votes["corbeau"] in votes_count.keys():
                    votes_count[self.village_votes["corbeau"]] += 2
                else:
                    votes_count[self.village_votes["corbeau"]] = 2
            max_votes = max(votes_count.values())
            # On cherche les joueurs qui ont le max
            max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
            # On regarde si il y a une égalité
            if len(max_votes_player) > 1:
                await ctx.guild.get_channel(GlobalChannel.VOTE.value).send("Il y a une égalité, les membres suivants sont donc en sursis pour le second vote : " + ", ".join([ctx.guild.get_member(player).mention for player in max_votes_player])) # type: ignore
                self.village_votes["votes"] = {}
                self.village_votes["choices"] = max_votes_player
                self.village_votes["is_vote"] = True
                self.village_votes["corbeau"] = 0
                return await ctx.respond("Un second vote est donc lancé !", ephemeral=True)
            await ctx.guild.get_member(max_votes_player[0]).add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
            await ctx.guild.get_member(max_votes_player[0]).remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
            await ctx.respond(f"{ctx.guild.get_member(max_votes_player[0]).name} a été tué !", ephemeral=True) # type: ignore
            # On reset les votes
            self.vote_cooldown = []
        self.village_votes["choices"] = []
        self.time = "nuit"
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).send("----------") # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=False, view_channel=True, reason="Passage à la nuit") # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).send("----------") # type: ignore 
        if self.village_votes["corbeau"] != 0:
            webhook = await get_webhook(self.bot, GlobalChannel.VOTE.value, "Vote")
            await webhook.send(f"Je vote contre <@{self.village_votes['corbeau']}> (+**2** votes)", username="🐦‍⬛ Corbeau", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1185951750461599896/black_bird.png")
        self.village_votes["corbeau"] = 0
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=False, view_channel=True, reason="Passage à la nuit")  # type: ignore
        await ctx.guild.get_channel(GlobalChannel.SUJET.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=False, view_channel=True, reason="Passage à la nuit") # type: ignore
        for thread in ctx.guild.get_channel(GlobalChannel.SUJET.value).threads: # type: ignore
            await thread.edit(locked=True, reason="Passage à la nuit")
        for user in ctx.guild.members: # type: ignore
            # Si l'utilisateur a accès a LOUP_CHAT et à LOUP_VOTE on lui redonne la permission d'écrire, sinon on passe
            if user in [member for member in ctx.guild.get_channel(Channels.LOUP_CHAT.value).members] and Roles.LG_VIVANT.value in [role.id for role in user.roles]: # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(user, send_messages=True, view_channel=True, reason="Passage à la nuit") # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(user, send_messages=True, view_channel=True, reason="Passage à la nuit") # type: ignore
        await ctx.respond("La nuit a été lancée !", ephemeral=True)


    @lg.command(name="mort", description="Permet de tuer un joueur")
    async def death(self, ctx: discord.ApplicationContext, member: discord.Member):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        # ON lui donne le role mort et on lui enleve le role vivant
        await member.add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
        await member.remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
        await ctx.respond(f"{member.name} a été tué !", ephemeral=True)


    vote = lg.create_subgroup(name="vote", description="Commandes pour voter")

    @vote.command(name="village", description="Permet aux villageois de voter contre un joueur")
    async def vote_village(self, ctx: discord.ApplicationContext, member: discord.Member, reason: discord.Option(str, description="La raison du vote", required=False)): # type: ignore
        if ctx.channel.id == Channels.CORBEAU.value: # type: ignore
            if self.village_votes["corbeau"] != 0:
                return await ctx.respond("Vous avez déjà voté !", delete_after=10)
            if ctx.author.id == member.id:
                return await ctx.respond("Vous ne pouvez pas voter contre vous même !", delete_after=10)
            if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value in [role.id for role in member.roles]:
                return await ctx.respond("Vous ne pouvez pas voter contre un mort !", delete_after=10)
            if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value not in [role.id for role in member.roles]:
                return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !", delete_after=10)
            self.village_votes["corbeau"] = member.id # type: ignore
            await ctx.respond(f"Vous avez voté contre {member.name} !", ephemeral=True)
            return
        if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
        if ctx.author.id == member.id:
            return await ctx.respond("Vous ne pouvez pas voter contre vous même !", delete_after=10)
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un mort !", delete_after=10)
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value not in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !", delete_after=10)
        if ctx.author.id in self.vote_cooldown:
            return await ctx.respond("Vous êtes en cooldown !", delete_after=10)
        if self.village_votes["choices"] != [] and member.id not in self.village_votes["choices"]: # type: ignore
            return await ctx.respond("Ce joueur n'est pas dans les choix !", delete_after=10)
        self.vote_cooldown.append(ctx.author.id) # type: ignore
        Timer(30, lambda: self.vote_cooldown.remove(ctx.author.id)).start() # type: ignore
        if not self.village_votes["is_vote"]:
            return await ctx.respond("Aucun vote n'est actuellement en cours", delete_after=10)
        if ctx.author.id in self.village_votes["votes"].keys(): # type: ignore
            deja_vote = True
        else:
            deja_vote = False
        self.village_votes["votes"][ctx.author.id] = member.id # type: ignore
        await ctx.respond(f"Vous avez voté contre {member.name} !", ephemeral=True)
        webhook = await get_webhook(self.bot, GlobalChannel.VOTE.value, "Vote")
        if deja_vote:
            await webhook.send(f"J'ai changé mon vote, je vote maintenant contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url) # type: ignore
        else:
            await webhook.send(f"Je vote contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url) # type: ignore
    

    @vote.command(name="loup", description="Permet aux loups de voter contre un joueur")
    async def vote_loup(self, ctx: discord.ApplicationContext, member: discord.Member, reason: discord.Option(str, description="La raison du vote", required=False)): # type: ignore
        if ctx.channel.id != Channels.LOUP_VOTE.value: # type: ignore
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
        if ctx.author.id == member.id:
            return await ctx.respond("Vous ne pouvez pas voter contre vous même !", delete_after=10)
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un mort !", delete_after=10)
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value not in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !", delete_after=10)
        if ctx.author.id in self.vote_cooldown:
            return await ctx.respond("Vous êtes en cooldown !", delete_after=10)
        if self.loup_votes["choices"] != [] and member.id not in self.loup_votes["choices"]: # type: ignore
            return await ctx.respond("Ce joueur n'est pas dans les choix !", delete_after=10)
        self.vote_cooldown.append(ctx.author.id)
        Timer(30, lambda: self.vote_cooldown.remove(ctx.author.id)).start()
        if not self.loup_votes["is_vote"]:
            return await ctx.respond("Aucun vote n'est actuellement en cours", delete_after=10)
        if ctx.author.id in self.loup_votes["votes"].keys(): # type: ignore
            deja_vote = True
        else:
            deja_vote = False
        self.loup_votes["votes"][ctx.author.id] = member.id # type: ignore
        await ctx.respond(f"Vous avez voté contre {member.name} !", ephemeral=True)
        webhook = await get_webhook(self.bot, Channels.LOUP_VOTE.value, "Vote")
        if deja_vote:
            await webhook.send(f"J'ai changé mon vote, je vote maintenant contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url) # type: ignore
        else:
            await webhook.send(f"Je vote contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url) # type: ignore

        

    @lg.command(name="most_voted", description="Permet d'envoyer un message privé au joueur le plus voté")
    async def most_voted(self, ctx: discord.ApplicationContext): # type: ignore
        if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
        if not self.village_votes["is_vote"]: # type: ignore
            return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
        votes_count = {}
        for vote in self.village_votes["votes"].values(): # type: ignore
            if vote not in votes_count.keys():
                votes_count[vote] = 1
            else:
                votes_count[vote] += 1
        # On cherche le max
        if self.village_votes["corbeau"] != 0:
            if self.village_votes["corbeau"] in votes_count.keys():
                votes_count[self.village_votes["corbeau"]] += 2
            else:
                votes_count[self.village_votes["corbeau"]] = 2
        max_votes = max(votes_count.values())
        # On cherche les joueurs qui ont le max
        max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
        # On regarde si il y a une égalité
        if len(max_votes_player) > 1:
            for player in max_votes_player:
                await ctx.guild.get_member(player).send(f"Vous êtes l'un des joueurs les plus votés ! Vous avez {max_votes} votes ! Défendez vous !") # type: ignore
        await ctx.guild.get_member(max_votes_player).send(f"Vous êtes le joueur le plus voté ! Vous avez {max_votes} votes ! Défendez vous !") # type: ignore
        

    @lg.command(name="unvote", description="Permet d'annuler son vote")
    async def unvote(self, ctx: discord.ApplicationContext):
        if ctx.channel.id == Channels.CORBEAU.value: # type: ignore
            if self.village_votes["corbeau"] == 0:
                return await ctx.respond("Vous n'avez pas voté !", delete_after=10)
            self.village_votes["corbeau"] = 0
            await ctx.respond("Votre vote a été annulé !", ephemeral=True)
            return
        if ctx.channel.id == GlobalChannel.VOTE.value: # type: ignore
            if ctx.author.id not in self.village_votes["votes"].keys(): # type: ignore
                return await ctx.respond("Vous n'avez pas voté !", delete_after=10)
            del self.village_votes["votes"][ctx.author.id] # type: ignore
            await ctx.respond("Votre vote a été annulé !", ephemeral=True)
        elif ctx.channel.id == Channels.LOUP_VOTE.value: # type: ignore
            if ctx.author.id not in self.loup_votes["votes"].keys(): # type: ignore
                return await ctx.respond("Vous n'avez pas voté !", delete_after=10)
            del self.loup_votes["votes"][ctx.author.id] # type: ignore
            await ctx.respond("Votre vote a été annulé !", ephemeral=True)
        else:
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)


    @lg.command(name="vote-list", description="Permet de voir les votes en cours")
    async def vote_list(self, ctx: discord.ApplicationContext):
        if ctx.channel.id in [GlobalChannel.VILLAGE.value, GlobalChannel.VOTE.value]: # type: ignore
            if not self.village_votes["is_vote"]: # type: ignore
                return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
            message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ Vote du village\n━━━━━━━━━━━━━━━━━━\n"
            # On affiche vote : nombre de votes (voteurs)
            for vote in self.village_votes["votes"].values(): # type: ignore
                member = ctx.guild.get_member(vote).mention # type: ignore
                vote_count = list(self.village_votes["votes"].values()).count(vote) # type: ignore
                if vote == self.village_votes["corbeau"]:
                    vote_count += 2
                message += f"{member} : {vote_count} {'(+**2** du corbeau)' if vote == self.village_votes['corbeau'] else ''} vote{'s' if vote_count > 1 else ''}\n" # type: ignore
            await ctx.respond(embed=discord.Embed(title="Votes", description=message), ephemeral=True)
        elif ctx.channel.id in [Channels.LOUP_CHAT.value, Channels.LOUP_VOTE.value]: # type: ignore
            if not self.loup_votes["is_vote"]:
                return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
            message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ Vote des loups\n━━━━━━━━━━━━━━━━━━\n"
            # On affiche vote : nombre de votes (voteurs)
            for vote in self.loup_votes['votes'].values(): # type: ignore
                member = ctx.guild.get_member(vote).mention # type: ignore
                vote_count = list(self.loup_votes['votes'].values()).count(vote) # type: ignore
                message += f"{member} : {vote_count} vote{'s' if vote_count > 1 else ''}\n" # type: ignore
            await ctx.respond(embed=discord.Embed(title="Votes", description=message), ephemeral=True)

        else:
            return await ctx.respond("Vous ne pouvez pas effectuer cette commande ici !", delete_after=10)
        
    @lg.command(name="findujour", description="Envoie un message pour prévenir que le jour va se terminer")
    async def findujour(self, ctx: discord.ApplicationContext, jour: discord.Option(int, description="Le jour en cours", required=True), heure: discord.Option(str, description="L'heure à laquelle le jour se terminera", required=True)): # type: ignore
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        webhook = await get_webhook(self.bot, GlobalChannel.ANNONCES_VILLAGE.value, "Annonces")
        await webhook.send(f"━━━━━━━━━━━━━━━━━━━━━\n⏲ | Fin du Jour {jour} à {heure} {ctx.guild.get_role(Roles.LG_VIVANT.value).mention}\n━━━━━━━━━━━━━━━━━━━━━", username=ctx.author.name, avatar_url=ctx.author.avatar.url) # type: ignore
        await ctx.respond("Message envoyé !", ephemeral=True)


    @commands.Cog.listener("on_message")
    async def on_message(self, message: discord.Message): 
        guild = message.guild
        if guild is None:  # Vérifie si le message est envoyé en mp
            # On envoie le message avec un webhook dans le channel AdminChannel.MP
            webhook = await get_webhook(self.bot, AdminChannel.MP.value, "MP")
            await webhook.send(message.content, username=message.author.name, avatar_url=message.author.avatar.url) # type: ignore
            return
        if message.channel.id == GlobalChannel.SUJET.value and not message.author.bot:
            await message.channel.create_thread(name=message.content if len(message.content) < 100 else message.content[:97] + "...", message=message, reason="Création d'un thread de discussion sur un sujet du jeu") # type: ignore
            await message.add_reaction("🟢")
            await message.add_reaction("🤔")
            await message.add_reaction("🔴")
        if message.channel.id == GlobalChannel.ANNONCES_VILLAGE.value and message.author.id in self.interview:
            self.interview.remove(message.author.id)
            await message.channel.set_permissions(message.author, send_messages=False) # type: ignore
            return
        if message.channel.id == Channels.LOUP_CHAT.value and message.author.id not in [self.bot.user.id, Users.LUXIO.value] and not message.author.bot: # type: ignore
            if message.content.startswith("!") or message.content.startswith("/"):
                return
            content = message.content
            contents = []
            contents.append(content[:1990 if len(content) > 1990 else len(content)])
            webhook = await get_webhook(self.bot, Channels.PETITE_FILLE.value, "🐺")
            while len(content) > 1990:
                contents.append(content[:1990])
                content = content[1990:]
            if message.author.id == self.LAST_MESSAGE_SENDER:
                if len(contents) > 1:
                    await webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    for part in contents[1:-1]:
                        await webhook.send(part, username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    await webhook.send(contents[-1], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                else:
                    await webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
            else:
                self.current_pp = 0 if self.current_pp == 1 else 1
                if len(contents) > 1:
                    await webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    for part in contents[1:-1]:
                        await webhook.send(part, username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    await webhook.send(contents[-1], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                else:
                    await webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                self.LAST_MESSAGE_SENDER = message.author.id


def setup(bot):
    bot.add_cog(LG(bot))
