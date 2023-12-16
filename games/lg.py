from discord.ext import commands
import discord
from threading import Timer
from enums import GlobalChannel, Roles, Channels, AdminChannel

async def get_webhook(bot, channel, name) -> discord.Webhook:
    try:
        webhook: discord.Webhook = await [webhook for webhook in await bot.get_channel(channel).webhooks() if webhook.name == name][0].edit(name=name) # type: ignore
    except IndexError:
        webhook: discord.Webhook = await bot.get_channel(channel).create_webhook(name=name) # type: ignore
    return webhook


class LG(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.interview: list = []
        self.LAST_MESSAGE_SENDER = 1
        self.current_pp: int = 0
        self.vote_cooldown: list[Timer] = []
        self.votes: dict[str, dict[int, int]] = {}
        self.current_vote: str | None = None
        self.time = "jour"
    
    @commands.slash_command(name="notif", description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
    async def notif(self, ctx: discord.ApplicationContext, role: discord.Role):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        await ctx.send_modal(Message([member for member in ctx.guild.members if role.id in [role.id for role in member.roles]], title="Quel message voulez vous envoyer ?")) # type: ignore

    @commands.slash_command(name="interview", description="Permet d'interviewer un joueur dans le salon #annonces-village")
    async def interview_command(self, ctx: discord.ApplicationContext, member: discord.Member):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        await ctx.guild.get_channel(GlobalChannel.ANNONCES_VILLAGE.value).set_permissions(member, send_messages=True) # type: ignore
        # On attends que le membre envoie un message
        await ctx.respond(f"Le channel a été ouvert pour {member.name}, vous pouvez lui poser vos questions !", ephemeral=True)
        self.interview.append(member.id)

    @commands.slash_command(name="jour", description="Permet de passer au jour suivant")
    async def day(self, ctx: discord.ApplicationContext):
        await ctx.response.defer()
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        if self.time == "jour":
            return await ctx.respond("Vous ne pouvez pas lancer un jour alors qu'un jour est déjà en cours", delete_after=10)
        self.time = "jour"
        name = f"Vote {len(self.votes.keys()) + 1}"
        self.votes[name] = {}
        self.current_vote = name
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=True) # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=True) # type: ignore
        for user in [user for user in ctx.guild.members if Roles.LG_VIVANT.value in [role.id for role in user.roles]]: # type: ignore
            # Si l'utilisateur a accès a LOUP_CHAT et à LOUP_VOTE on lui redonne la permission d'écrire, sinon on passe
            if user in [member for member in ctx.guild.get_channel(Channels.LOUP_CHAT).members]: # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(user, send_messages=False) # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(user, send_messages=False) # type: ignore
        await ctx.respond("Le jour a été lancé !", ephemeral=True)

    @commands.slash_command(name="nuit", description="Permet de passer à la nuit suivante")
    async def night(self, ctx: discord.ApplicationContext):
        await ctx.response.defer()
        if not ctx.author.guild_permissions.administrator:  # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        if self.time == "nuit":
            return await ctx.respond("Vous ne pouvez pas lancer une nuit alors qu'une nuit est déjà en cours", delete_after=10)
        self.time = "nuit"
        # On compte les votes
        if self.current_vote is not None:
            votes_count = {}
            for vote in self.votes[current_vote].values(): # type: ignore
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
                await ctx.respond(f"Il y a une égalité ! Les joueurs désignés sont {', '.join(['<@'+ player + '>' for player in max_votes_player])}")
            else:
                # On le tue
                await ctx.guild.get_member(max_votes_player[0]).add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
                await ctx.guild.get_member(max_votes_player[0]).remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
                await ctx.respond(f"{ctx.guild.get_member(max_votes_player[0]).name} a été tué !", ephemeral=True) # type: ignore
                # On reset les votes
                self.current_vote = None
                self.vote_cooldown = []
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).send("----------") # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VILLAGE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=False) # type: ignore
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).send("----------") # type: ignore 
        await ctx.guild.get_channel(GlobalChannel.VOTE.value).set_permissions(ctx.guild.get_role(Roles.LG_VIVANT.value), send_messages=False)  # type: ignore
        for user in [user for user in ctx.guild.members if Roles.LG_VIVANT.value in [role.id for role in user.roles]]: # type: ignore
            # Si l'utilisateur a accès a LOUP_CHAT et à LOUP_VOTE on lui redonne la permission d'écrire, sinon on passe
            if user in [member for member in ctx.guild.get_channel(Channels.LOUP_CHAT.value).members]: # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_CHAT.value).set_permissions(user, send_messages=True) # type: ignore
                await ctx.guild.get_channel(Channels.LOUP_VOTE.value).set_permissions(user, send_messages=True) # type: ignore
        await ctx.respond("La nuit a été lancée !", ephemeral=True)


    @commands.slash_command(name="mort", description="Permet de tuer un joueur")
    async def death(self, ctx: discord.ApplicationContext, member: discord.Member):
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        # ON lui donne le role mort et on lui enleve le role vivant
        await member.add_roles(ctx.guild.get_role(Roles.LG_MORT.value), reason="Joueur tué") # type: ignore
        await member.remove_roles(ctx.guild.get_role(Roles.LG_VIVANT.value), reason="Joueur tué") # type: ignore
        await ctx.respond(f"{member.name} a été tué !", ephemeral=True)



    @commands.slash_command(name="vote", description="Permet de voter contre un joueur")
    async def vote(self, ctx: discord.ApplicationContext, member: discord.Member, reason: discord.Option(str, description="La raison du vote", required=False)): # type: ignore
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
        self.vote_cooldown.append(ctx.author.id) # type: ignore
        Timer(30, lambda: self.vote_cooldown.remove(ctx.author.id)).start() # type: ignore
        # On ajoute le vote sous la forme vote["nom_du_vote"][votant] = vote
        if self.current_vote not in self.votes.keys():
            return await ctx.respond("Aucun vote n'est actuellement en cours", delete_after=10)
        if ctx.author.id in self.votes[self.current_vote].keys(): # type: ignore
            deja_vote = True
        else:
            deja_vote = False
        self.votes[self.current_vote][ctx.author.id] = member.id # type: ignore
        await ctx.respond(f"Vous avez voté contre {member.name} !", ephemeral=True)
        webhook = await get_webhook(self.bot, GlobalChannel.VOTE.value, "Vote")
        if deja_vote:
            await webhook.send(f"J'ai changé mon vote, je vote maintenant contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.name, avatar_url=ctx.author.avatar.url) # type: ignore
        else:
            await webhook.send(f"Je contre {member.mention} {'car '+ reason if reason is not None else ''}", username=ctx.author.name, avatar_url=ctx.author.avatar.url) # type: ignore


    @commands.slash_command(name="unvote", description="Permet d'annuler son vote")
    async def unvote(self, ctx: discord.ApplicationContext):
        if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
        if ctx.author.id not in self.votes[self.current_vote].keys(): # type: ignore
            return await ctx.respond("Vous n'avez pas voté !", delete_after=10)
        del self.votes[self.current_vote][ctx.author.id] # type: ignore
        await ctx.respond("Votre vote a été annulé !", ephemeral=True)


    @commands.slash_command(name="vote-list", description="Permet de voir les votes en cours")
    async def vote_list(self, ctx: discord.ApplicationContext):
        if ctx.channel.id != GlobalChannel.VOTE.value: # type: ignore
            return await ctx.respond("Vous ne pouvez pas voter ici !", delete_after=10)
        if self.current_vote is None:
            return await ctx.respond("Aucun vote n'est en cours !", delete_after=10)
        message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ Vote {self.current_vote}\n━━━━━━━━━━━━━━━━━━\n"
        # On affiche vote : nombre de votes (voteurs)
        for vote in self.votes[self.current_vote].values(): # type: ignore
            message += f"{ctx.guild.get_member(vote).mention} : {list(self.votes[self.current_vote].values()).count(vote)} ({len([votant for votant in self.votes[self.current_vote].keys() if self.votes[self.current_vote][votant] == vote])})\n" # type: ignore
        await ctx.respond(embed=discord.Embed(title="Votes", description=message), ephemeral=True)
        
    @commands.slash_command(name="findujour", description="Envoie un message pour prévenir que le jour va se terminer")
    async def findujour(self, ctx: discord.ApplicationContext, jour: discord.Option(int, description="Le jour en cours", required=True), heure: discord.Option(str, description="L'heure à laquelle le jour se terminera", required=True)): # type: ignore
        if not ctx.author.guild_permissions.administrator: # type: ignore
            return await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande !", delete_after=10)
        await ctx.guild.get_channel(GlobalChannel.ANNONCES_VILLAGE.value).send(f"━━━━━━━━━━━━━━━━━━━━━\n⏲ | Fin du Jour {jour} à {heure} {ctx.guild.get_role(Roles.LG_VIVANT.value).mention}\n━━━━━━━━━━━━━━━━━━━━━") # type: ignore
        await ctx.respond("Message envoyé !", ephemeral=True)


    @commands.Cog.listener("on_message")
    async def on_message(self, message: discord.Message): 
        guild = message.guild
        if guild is None:  # Vérifie si le message est envoyé en mp
            # On envoie le message avec un webhook dans le channel AdminChannel.MP
            webhook = await get_webhook(self.bot, AdminChannel.MP.value, "MP")
            await webhook.send(message.content, username=message.author.name, avatar_url=message.author.avatar.url) # type: ignore
            return
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
            current_webhook = await get_webhook(self.bot, Channels.PETITE_FILLE.value, "🐺")
            while len(content) > 1990:
                contents.append(content[:1990])
                content = content[1990:]
            if message.author.id == self.LAST_MESSAGE_SENDER:
                if len(contents) > 1:
                    await current_webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    for part in contents[1:-1]:
                        await current_webhook.send(part, username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    await current_webhook.send(contents[-1], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                else:
                    await current_webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
            else:
                self.current_pp = 0 if self.current_pp == 1 else 1
                if len(contents) > 1:
                    await current_webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    for part in contents[1:-1]:
                        await current_webhook.send(part, username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png") # type: ignore
                    await current_webhook.send(contents[-1], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                else:
                    await current_webhook.send(contents[0], username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", avatar_url="https://media.discordapp.net/attachments/939233865350938644/1184888656222244905/wolf.png" if self.current_pp == 0 else "https://media.discordapp.net/attachments/939233865350938644/1184890615650062356/wolf.png", files=message.attachments) # type: ignore
                self.LAST_MESSAGE_SENDER = message.author.id


def setup(bot):
    bot.add_cog(LG(bot))
