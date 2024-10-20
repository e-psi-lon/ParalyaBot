from collections import Counter
from typing import Literal
from ._lg import *


class LG(commands.Cog):
    """
    This is a Cog class for the Discord bot. This Cog is specifically designed for managing the Loup-Garou game.
    It includes commands for game management such as voting, setting roles, transitioning between day and night,
    and more. It also handles certain events related to the game.

    Attributes:
        bot: An instance of commands.Bot. This represents the bot that's being used. interview: A list that
        stores the IDs of members being interviewed.
        last_message_sender: An integer that stores the ID of the last
        member who sent a message in the #loup-chat channel.
        current_pp: An integer that represents the current pseudonym
        index and profile picture index for anonymous messages.
        village_votes: A dictionary that stores the voting data for the village.
        loup_votes: A dictionary that stores the voting data for the werewolves.
        time: A string that represents the current time of day in the game ("nuit" for night, "jour" for day).
        roles: A dictionary that stores specific roles for the game, such as the "LOUP_BAVARD" role.
    """
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.interview: list = []
        self.last_message_sender = 1
        self.current_pp: Literal[0, 1] = 0
        self.village_votes: dict[str, dict[int, int] | bool | list[int] | int] = {"is_vote": False, "votes": {},
                                                                                  "choices": [], "corbeau": 0}
        self.loup_votes: dict[str, dict[int, int] | bool | list[int]] = {"is_vote": False, "votes": {}, "choices": []}
        self.time = "nuit"
        self.roles: dict[str, LoupBavard | None] = {
            "LOUP_BAVARD": None
        }

    lg = discord.SlashCommandGroup(name="lg", description="Commandes pour le Loup-Garou")

    @lg.command(name="notif",
                description="Envoie un mp d'info Loup-Garou à tout les joueurs possédant un rôle spécifique")
    @admin_only()
    async def notif(self, ctx: discord.ApplicationContext, role: discord.Role):
        await ctx.send_modal(
            Message(message_callback, members=[member for member in ctx.guild.members if role.id in
                                               [role.id for role in member.roles]],
                    title="Quel message voulez vous envoyer ?"))

    @lg.command(name="interview", description="Permet d'interviewer un joueur dans le salon #annonces-village")
    @admin_only()
    async def interview_command(self, ctx: discord.ApplicationContext, member: discord.Member):
        await ctx.guild.get_channel(LgGlobalChannel.RESUME).set_permissions(member, send_messages=True)
        # On attend que le membre envoie un message
        await ctx.respond(f"Le channel a été ouvert pour {member.display_name}, vous pouvez lui poser vos questions !",
                          ephemeral=True)
        self.interview.append(member.id)

    @lg.command(name="jour", description="Permet de passer au jour suivant")
    @admin_only()
    async def day(self, ctx: discord.ApplicationContext,
                  force: discord.Option(bool, description="Force le passage au jour", required=False,
                                        default=False),  # type: ignore
                  kill: discord.Option(bool, description="Tue le joueur le plus voté par les loups", required=False,
                                       default=True)):  # type: ignore
        await ctx.response.defer()
        if self.time == "jour":
            return await ctx.respond("Vous ne pouvez pas lancer un jour alors qu'un jour est déjà en cours",
                                     ephemeral=True)
        self.village_votes["is_vote"] = True
        self.village_votes["votes"] = {}
        self.loup_votes["is_vote"] = False
        # On tue le joueur le plus voté par les loups
        if len(self.loup_votes["votes"].keys()) > 0:
            votes_count = Counter(self.loup_votes["votes"].values())
            # Si il y a vote du corbeau on l'ajoute
            max_votes = max(votes_count.values())
            # On cherche les joueurs qui ont le max
            max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
            if len(max_votes_player) > 1 and not force:
                webhook = await get_webhook(self.bot, LgChannels.LOUP_VOTE, "🐺")
                await webhook.send("Il y a une égalité, décidez vous sur qui tuer : " + ", ".join(
                    [ctx.guild.get_member(player).mention for player in max_votes_player]), username="ParalyaLG",
                                   avatar_url=get_asset("paralya_lg"))
                self.loup_votes["votes"] = {}
                self.loup_votes["choices"] = max_votes_player
                self.loup_votes["is_vote"] = True
                return await ctx.respond("Un second vote est donc lancé !", ephemeral=True)
            if len(max_votes_player) == 1 and kill:
                # On le tue
                await ctx.guild.get_member(max_votes_player[0]).add_roles(ctx.guild.get_role(LgRoles.LG_MORT),
                                                                          reason="Joueur tué")
                await ctx.guild.get_member(max_votes_player[0]).remove_roles(
                    ctx.guild.get_role(LgRoles.LG_VIVANT),
                    reason="Joueur tué")
                await ctx.respond(f"{ctx.guild.get_member(max_votes_player[0]).display_name} a été tué !", ephemeral=True)
        self.loup_votes["choices"] = []
        self.time = "jour"
        await ctx.guild.get_channel(LgGlobalChannel.VILLAGE).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT), send_messages=True, view_channel=True,
            reason="Passage au jour")
        await ctx.guild.get_channel(LgGlobalChannel.VOTE).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT),
            send_messages=True, view_channel=True,
            reason="Passage au jour")
        await ctx.guild.get_channel(LgGlobalChannel.SUJET).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT), send_messages=True,
            view_channel=True, reason="Passage au jour")
        for thread in ctx.guild.get_channel(LgGlobalChannel.SUJET).threads:
            await thread.edit(locked=False, reason="Passage au jour")
        for user in ctx.guild.members:
            # Si l'utilisateur a accès à LOUP_CHAT et à LOUP_VOTE, on lui redonne la permission d'écrire, sinon on passe
            if (user in [member for member in
                         ctx.guild.get_channel(LgChannels.LOUP_CHAT).members] and LgRoles.LG_VIVANT in
                    [role.id for role in user.roles]):
                await ctx.guild.get_channel(LgChannels.LOUP_CHAT).set_permissions(user, send_messages=False,
                                                                                        view_channel=True,
                                                                                        reason="Passage au jour")
                await ctx.guild.get_channel(LgChannels.LOUP_VOTE).set_permissions(user, send_messages=False,
                                                                                        view_channel=True,
                                                                                        reason="Passage au jour")
        await ctx.respond("Le jour a été lancé !", ephemeral=True)

    @lg.command(name="nuit", description="Permet de passer à la nuit suivante")
    @admin_only()
    async def night(self, ctx: discord.ApplicationContext,
                    force: discord.Option(bool, description="Force le passage à la nuit", required=False,
                                          default=False),  # type: ignore
                    kill: discord.Option(bool, description="Tue le joueur le plus voté par les villageois",
                                         required=False,
                                         default=True)):  # type: ignore
        await ctx.response.defer()
        if self.time == "nuit":
            return await ctx.respond("Vous ne pouvez pas lancer une nuit alors qu'une nuit est déjà en cours",
                                     ephemeral=True)
        # On compte les votes
        self.loup_votes["is_vote"] = True
        self.loup_votes["votes"] = {}
        self.village_votes["is_vote"] = False
        if len(self.village_votes["votes"].keys()) > 0:
            votes_count = Counter(self.village_votes["votes"].values())
            # Si il y a vote du corbeau on l'ajoute
            if self.village_votes["corbeau"] != 0:
                if self.village_votes["corbeau"] in votes_count.keys():
                    votes_count[self.village_votes["corbeau"]] += 2
                else:
                    votes_count[self.village_votes["corbeau"]] = 2
            max_votes = max(votes_count.values())
            # On cherche les joueurs qui ont le max
            max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
            # On regarde s'il y a une égalité
            if len(max_votes_player) > 1 and not force:
                webhook = await get_webhook(self.bot, LgGlobalChannel.VOTE, "🐺")
                await webhook.send(
                    "Il y a une égalité, les membres suivants sont donc en sursis pour le second vote : " + ", ".join(
                        [ctx.guild.get_member(player).mention for player in max_votes_player]), username="ParalyaLG",
                    avatar_url=get_asset("paralya_lg"))
                self.village_votes["votes"] = {}
                self.village_votes["choices"] = max_votes_player
                self.village_votes["is_vote"] = True
                self.village_votes["corbeau"] = 0
                return await ctx.respond("Un second vote est donc lancé !", ephemeral=True)
            if len(max_votes_player) == 1 and kill:
                await ctx.guild.get_member(max_votes_player[0]).add_roles(ctx.guild.get_role(LgRoles.LG_MORT),
                                                                          reason="Joueur tué")
                await ctx.guild.get_member(max_votes_player[0]).remove_roles(
                    ctx.guild.get_role(LgRoles.LG_VIVANT),
                    reason="Joueur tué")
                await ctx.respond(f"{ctx.guild.get_member(max_votes_player[0]).display_name} a été tué !", ephemeral=True)
        self.village_votes["choices"] = []
        self.time = "nuit"
        webhook = await get_webhook(self.bot, LgGlobalChannel.VILLAGE, "🐺")
        await webhook.send("----------", username="ParalyaLG", avatar_url=get_asset("paralya_lg"))
        await ctx.guild.get_channel(LgGlobalChannel.VILLAGE).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT), send_messages=False, view_channel=True,
            reason="Passage à la nuit")
        webhook = await get_webhook(self.bot, LgGlobalChannel.VOTE, "🐺")
        await webhook.send("----------", username="ParalyaLG", avatar_url=get_asset("paralya_lg"))
        if self.village_votes["corbeau"] != 0:
            webhook = await get_webhook(self.bot, LgGlobalChannel.VOTE, "🐺")
            await webhook.send(f"Je vote contre <@{self.village_votes['corbeau']}> (+**2** votes)",
                               username="🐦‍⬛ Corbeau",
                               avatar_url=get_asset("black_bird")
                               )
        self.village_votes["corbeau"] = 0
        await ctx.guild.get_channel(LgGlobalChannel.VOTE).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT),
            send_messages=False, view_channel=True,
            reason="Passage à la nuit")
        await ctx.guild.get_channel(LgGlobalChannel.SUJET).set_permissions(
            ctx.guild.get_role(LgRoles.LG_VIVANT), send_messages=False, view_channel=True,
            reason="Passage à la nuit")
        for thread in ctx.guild.get_channel(LgGlobalChannel.SUJET).threads:
            await thread.edit(locked=True, reason="Passage à la nuit")
        vivants = [member for member in ctx.guild.get_role(LgRoles.LG_VIVANT).members if
                   LgRoles.LG_VIVANT in [role.id for role in member.roles]]
        loups = ctx.guild.get_channel(LgChannels.LOUP_CHAT).members
        for user in vivants:
            # Si l'utilisateur a accès à LOUP_CHAT et à LOUP_VOTE, on lui redonne la permission d'écrire, sinon on passe
            if user in loups:
                await ctx.guild.get_channel(LgChannels.LOUP_CHAT).set_permissions(user, send_messages=True,
                                                                                        view_channel=True,
                                                                                        reason="Passage à la nuit")
                await ctx.guild.get_channel(LgChannels.LOUP_VOTE).set_permissions(user, send_messages=True,
                                                                                        view_channel=True,
                                                                                        reason="Passage à la nuit")
        await ctx.respond("La nuit a été lancée !", ephemeral=True)

    @lg.command(name="mort", description="Permet de tuer un joueur")
    @admin_only()
    async def death(self, ctx: discord.ApplicationContext, member: discord.Member):
        # On lui donne le role mort et on lui enlève le role vivant
        await member.add_roles(ctx.guild.get_role(LgRoles.LG_MORT), reason="Joueur tué")
        await member.remove_roles(ctx.guild.get_role(LgRoles.LG_VIVANT), reason="Joueur tué")
        await ctx.respond(f"{member.display_name} a été tué !", ephemeral=True)

    vote = lg.create_subgroup(name="vote", description="Commandes pour voter")

    @vote.command(name="village", description="Permet aux villageois de voter contre un joueur")
    @commands.cooldown(1, 30, commands.BucketType.user)
    @check_valid_vote
    async def vote_village(self, ctx: discord.ApplicationContext, member: discord.Member,
                           reason: discord.Option(str, description="La raison du vote",
                                                  required=False)):  # type: ignore
        if ctx.channel.id == LgChannels.CORBEAU:
            if self.village_votes["corbeau"] != 0:
                return await ctx.respond("Vous avez déjà voté !", ephemeral=True)
            self.village_votes["corbeau"] = member.id
            await ctx.respond(f"Vous avez voté contre {member.display_name} !", ephemeral=True)
            return
        if ctx.channel.id != LgGlobalChannel.VOTE:
            return await ctx.respond("Vous ne pouvez pas voter ici !", ephemeral=True)
        if self.village_votes["choices"] != [] and member.id not in self.village_votes["choices"]:
            return await ctx.respond("Ce joueur n'est pas dans les choix !", ephemeral=True)
        if not self.village_votes["is_vote"]:
            return await ctx.respond("Aucun vote n'est actuellement en cours", ephemeral=True)
        if ctx.author.id in self.village_votes["votes"].keys():
            deja_vote = True
        else:
            deja_vote = False
        self.village_votes["votes"][ctx.author.id] = member.id
        await ctx.respond(f"Vous avez voté contre {member.display_name} !", ephemeral=True)
        webhook = await get_webhook(self.bot, LgGlobalChannel.VOTE, "🐺")
        if deja_vote:
            await webhook.send(
                f"J'ai changé mon vote, je vote maintenant contre "
                f"{member.mention} {'car ' + reason if reason is not None else ''}",
                username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url)
        else:
            await webhook.send(f"Je vote contre {member.mention} {'car ' + reason if reason is not None else ''}",
                               username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url)

    @vote_village.error
    async def vote_village_error(self, ctx: discord.ApplicationContext, error):
        if isinstance(error, commands.CommandOnCooldown):
            await ctx.respond(f"Vous êtes en cooldown ! Veuillez réessayer dans {round(error.retry_after)} secondes !",
                              ephemeral=True)
        else:
            await ctx.respond(f"Une erreur est survenue : {error}", ephemeral=True)

    @vote.command(name="loup", description="Permet aux loups de voter contre un joueur")
    @commands.cooldown(1, 30, commands.BucketType.user)
    @check_valid_vote
    async def vote_loup(self, ctx: discord.ApplicationContext, member: discord.Member,
                        reason: discord.Option(str, description="La raison du vote", required=False)):  # type: ignore
        if ctx.channel.id != LgChannels.LOUP_VOTE:
            return await ctx.respond("Vous ne pouvez pas voter ici !", ephemeral=True)
        if self.loup_votes["choices"] != [] and member.id not in self.loup_votes["choices"]:
            return await ctx.respond("Ce joueur n'est pas dans les choix !", ephemeral=True)
        if not self.loup_votes["is_vote"]:
            return await ctx.respond("Aucun vote n'est actuellement en cours", ephemeral=True)
        if ctx.author.id in self.loup_votes["votes"].keys():
            deja_vote = True
        else:
            deja_vote = False
        self.loup_votes["votes"][ctx.author.id] = member.id
        await ctx.respond(f"Vous avez voté contre {member.display_name} !", ephemeral=True)
        webhook = await get_webhook(self.bot, LgChannels.LOUP_VOTE, "🐺")
        if deja_vote:
            await webhook.send(
                f"J'ai changé mon vote, je vote maintenant contre {member.mention} "
                f"{'car ' + reason if reason is not None else ''}",
                username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url)
        else:
            await webhook.send(f"Je vote contre {member.mention} {'car ' + reason if reason is not None else ''}",
                               username=ctx.author.display_name, avatar_url=ctx.author.display_avatar.url)

    @vote_loup.error
    async def vote_loup_error(self, ctx: discord.ApplicationContext, error):
        if isinstance(error, commands.CommandOnCooldown):
            await ctx.respond(f"Vous êtes en cooldown ! Veuillez réessayer dans {round(error.retry_after)} secondes !",
                              ephemeral=True)
        else:
            await ctx.respond(f"Une erreur est survenue : {error}", ephemeral=True)

    @lg.command(name="most-voted", description="Permet d'envoyer un message privé au joueur le plus voté")
    @admin_only()
    async def most_voted(self, ctx: discord.ApplicationContext):
        if ctx.channel.id != LgGlobalChannel.VOTE:
            return await ctx.respond("Vous ne pouvez pas utiliser cette commande ici !", ephemeral=True)
        if not self.village_votes["is_vote"]:
            return await ctx.respond("Aucun vote n'est en cours !", ephemeral=True)
        votes_count = Counter(self.village_votes["votes"].values())
        # Si il y a vote du corbeau on l'ajoute
        if self.village_votes["corbeau"] != 0:
            if self.village_votes["corbeau"] in votes_count.keys():
                votes_count[self.village_votes["corbeau"]] += 2
            else:
                votes_count[self.village_votes["corbeau"]] = 2
        max_votes = max(votes_count.values())
        # On cherche les joueurs qui ont le max
        max_votes_player = [player for player, votes in votes_count.items() if votes == max_votes]
        # On regarde s'il y a une égalité
        if len(max_votes_player) > 1:
            for player in max_votes_player:
                await ctx.guild.get_member(player).send(
                    f"Vous êtes l'un des joueurs les plus votés ! Vous avez {max_votes} votes ! Défendez vous !")
        await ctx.guild.get_member(max_votes_player[0]).send(
            f"Vous êtes le joueur le plus voté ! Vous avez {max_votes} votes ! Défendez vous !")

    @lg.command(name="unvote", description="Permet d'annuler son vote")
    async def unvote(self, ctx: discord.ApplicationContext,
                     previous_id: discord.Option(int, description="L'id du précédent message de vote",
                                                 required=False)):  # type: ignore
        channel_votes_map = {
            LgChannels.CORBEAU: ("corbeau", self.village_votes),
            LgGlobalChannel.VOTE: ("votes", self.village_votes),
            LgChannels.LOUP_VOTE: ("votes", self.loup_votes),
        }
        if ctx.channel.id in channel_votes_map:
            vote_key, vote_dict = channel_votes_map[ctx.channel.id]
            if vote_key == "corbeau":
                if vote_dict[vote_key] == 0:
                    return await ctx.respond("Vous n'avez pas voté !", ephemeral=True)
                vote_dict[vote_key] = 0
            else:
                if ctx.author.id not in vote_dict[vote_key]:
                    return await ctx.respond("Vous n'avez pas voté !", ephemeral=True)
                vote_dict[vote_key].pop(ctx.author.id, None)
            await ctx.respond("Votre vote a été annulé !", ephemeral=True)
            if previous_id is not None:
                await (await ctx.channel.fetch_message(previous_id)).delete()
        else:
            return await ctx.respond("Vous ne pouvez pas voter ici (donc pas enlever le précédent vote)!",
                                     ephemeral=True)

    @admin_only()
    @lg.command(name="vote-reset", description="Permet de réinitialiser les votes")
    async def vote_reset(self, ctx: discord.ApplicationContext):
        self.village_votes = {"is_vote": self.time == "jour", "votes": {}, "choices": [], "corbeau": 0}
        self.loup_votes = {"is_vote": self.time == "nuit", "votes": {}, "choices": []}
        await ctx.respond("Votes réinitialisés !", ephemeral=True)

    @lg.command(name="vote-list", description="Permet de voir les votes en cours")
    async def vote_list(self, ctx: discord.ApplicationContext):
        if ctx.channel.id in [LgGlobalChannel.VILLAGE, LgGlobalChannel.VOTE]:
            if not self.village_votes["is_vote"]:
                return await ctx.respond("Aucun vote n'est en cours !", ephemeral=True)
            message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ Vote du village\n━━━━━━━━━━━━━━━━━━\n"
            # On affiche vote : nombre de votes (votant)4
            votes_count = Counter(self.village_votes['votes'].values())
            # Si il y a vote du corbeau on l'ajoute
            if self.village_votes["corbeau"] != 0:
                if self.village_votes["corbeau"] in votes_count.keys():
                    votes_count[self.village_votes["corbeau"]] += 2
                else:
                    votes_count[self.village_votes["corbeau"]] = 2
            for vote in votes_count.keys():
                member = ctx.guild.get_member(vote).mention
                vote_count = votes_count[vote]
                voters = [ctx.guild.get_member(voter).mention for voter in self.village_votes['votes'].keys() if
                          self.village_votes['votes'][voter] == vote]
                message += (f"{member} : {vote_count} vote{'s' if vote_count > 1 else ''} "
                            f"{'dont **2** du corbeau' if vote == self.village_votes['corbeau'] else ''} "
                            f"{'(' + ', '.join(voters) + ')' if len(voters) > 0 else ''}\n")
            await ctx.respond(embed=discord.Embed(title="Votes", description=message), ephemeral=True)
        elif ctx.channel.id in [LgChannels.LOUP_CHAT, LgChannels.LOUP_VOTE]:
            if not self.loup_votes["is_vote"]:
                return await ctx.respond("Aucun vote n'est en cours !", ephemeral=True)
            message = f"━━━━━━━━━━━━━━━━━━\n🐺 LGVote ¦ Vote des loups\n━━━━━━━━━━━━━━━━━━\n"
            # On affiche vote : nombre de votes (votant)
            votes_count = Counter(self.loup_votes['votes'].values())
            for vote in votes_count.keys():
                member = ctx.guild.get_member(vote).mention
                vote_count = votes_count[vote]
                voters = [ctx.guild.get_member(voter).mention for voter in self.loup_votes['votes'].keys() if
                          self.loup_votes['votes'][voter] == vote]
                message += (f"{member} : {vote_count} vote{'s' if vote_count > 1 else ''} "
                            f"{'(' + ', '.join(voters) + ')' if len(voters) > 0 else ''}\n")
            await ctx.respond(embed=discord.Embed(title="Votes", description=message), ephemeral=True)
        else:
            return await ctx.respond("Vous ne pouvez pas effectuer cette commande ici !", ephemeral=True)

    @lg.command(name="findujour", description="Envoie un message pour prévenir que le jour va se terminer")
    @admin_only()
    async def findujour(self, ctx: discord.ApplicationContext,
                        jour: discord.Option(int, description="Le jour en cours", required=True),  # type: ignore
                        heure: discord.Option(str, description="L'heure à laquelle le jour se terminera",
                                              # type: ignore
                                              required=True)):
        webhook = await get_webhook(self.bot, LgGlobalChannel.ANNONCES_VILLAGE, "🐺")
        await webhook.send(
            f"━━━━━━━━━━━━━━━━━━━━━\n⏲ | Fin du Jour {jour} à {heure}"
            f"{ctx.guild.get_role(LgRoles.LG_VIVANT).mention}\n━━━━━━━━━━━━━━━━━━━━━",
            username="ParalyaLG",
            avatar_url=get_asset("paralya_lg"))
        await ctx.respond("Message envoyé !", ephemeral=True)

    @lg.command(name="setrole", description="Permet de définir un rôle")
    @admin_only()
    async def setrole(self, ctx: discord.ApplicationContext,
                      role: discord.Option(LgGameRoles, description="Le rôle à définir", required=True),  # type: ignore
                      member: discord.Option(discord.Member, description="Le membre à qui définir le rôle",
                                             # type: ignore
                                             required=True)):
        match role:
            case LgGameRoles.LOUP_BAVARD:
                self.roles["LOUP_BAVARD"] = LoupBavard(member.id, self.bot)
                await ctx.respond("Rôle défini !", ephemeral=True)
            case _:
                await ctx.respond("Ce rôle n'est pas encore implémenté !", ephemeral=True)

    @lg.command(name="setmot", description="Permet de définir le mot du loup bavard")
    @admin_only()
    async def setmot(self, ctx: discord.ApplicationContext,
                     mot: discord.Option(str, description="Le mot à définir", required=True)):  # type: ignore
        if self.roles["LOUP_BAVARD"] is None:
            return await ctx.respond("Le rôle n'est pas défini !", ephemeral=True)
        self.roles["LOUP_BAVARD"].mot_actuel = mot
        await ctx.respond("Mot défini !", ephemeral=True)

    @commands.Cog.listener("on_message")
    async def on_message(self, message: discord.Message):
        guild = message.guild
        if (guild is None and message.content != "" and message.content is not None
                and message.author.id != self.bot.user.id and not message.author.bot):
            webhook = await get_webhook(self.bot, LgAdminChannel.MP, "MP")
            await webhook.send(message.content, username=message.author.display_name, avatar_url=message.author.avatar.url)
            return
        if message.channel.id == LgChannels.DATE_MYSTERE and not message.author.bot:
            webhook = await get_webhook(self.bot, LgChannels.CUPIDON, "🐺")
            await webhook.send(message.content, username=message.author.display_name, avatar_url=message.author.avatar.url)
            return
        if message.channel.id == LgGlobalChannel.SUJET and not message.author.bot:
            await message.channel.create_thread(
                name=message.content if len(message.content) < 100 else message.content[:97] + "...", message=message,
                reason="Création d'un thread de discussion sur un sujet du jeu")
            await message.add_reaction("🟢")
            await message.add_reaction("🤔")
            await message.add_reaction("🔴")
            return
        if message.channel.id == LgGlobalChannel.RESUME and message.author.id in self.interview:
            self.interview.remove(message.author.id)
            await message.channel.set_permissions(message.author, send_messages=False)
            return
        if (self.roles["LOUP_BAVARD"] is not None and
                message.channel.id == LgGlobalChannel.VILLAGE and message.author.id == self.roles[
                    'LOUP_BAVARD'].player_id):
            # Si le message contient le mot
            if self.roles['LOUP_BAVARD'].mot_actuel in message.content:
                self.roles['LOUP_BAVARD'].mots_places += 1
                self.roles['LOUP_BAVARD'].mot_place = True
                if self.roles['LOUP_BAVARD'].mots_places == 3:
                    webhook = await get_webhook(self.bot, LgChannels.LOUP_BAVARD, "🐺")
                    await webhook.send(
                        f"<@{Users.LUXIO}> Le loup bavard a placé son mot 3 fois !"
                        f" Il a donc droit à l'identité d'un joueur aléatoire !",
                        username="ParalyaLG",
                        avatar_url=get_asset("paralya_lg"))
                    self.roles['LOUP_BAVARD'].mots_places = 0
                    self.roles['LOUP_BAVARD'].mot_actuel = None
                    self.roles['LOUP_BAVARD'].mot_place = False
                    return
        if (message.channel.id == LgChannels.LOUP_CHAT and message.author.id not in
                (self.bot.user.id, Users.LUXIO) and not message.author.bot):
            if message.content.startswith("!") or message.content.startswith("/"):
                return
            content = message.content
            contents = [content[i:i + 2000] for i in range(0, len(content), 2000)]
            webhook = await get_webhook(self.bot, LgChannels.PETITE_FILLE, "🐺")
            if message.author.id != self.last_message_sender:
                self.current_pp = 0 if self.current_pp == 1 else 1
            username = "🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme"
            avatar_url = get_asset("wolf_variant_1") \
                if self.current_pp == 0 else get_asset("wolf_variant_2")
            self.last_message_sender = message.author.id
            answer = message.reference
            if answer is not None and (
                    await message.channel.fetch_message(answer.message_id)).author.id != Users.LUXIO:
                answer = await message.channel.fetch_message(answer.message_id)
                answer = discord.Embed(title="En réponse à", description=answer.content)
            if len(contents) > 1:
                await webhook.send(contents[0], username=username, avatar_url=avatar_url)
                for part in contents[1:-1]:
                    await webhook.send(part, username=username, avatar_url=avatar_url)
                await webhook.send(contents[-1], username=username, avatar_url=avatar_url,
                                   files=message.attachments if len(message.attachments) > 0 else discord.MISSING,
                                   embed=answer if isinstance(answer, discord.Embed) else discord.MISSING)
            else:
                await webhook.send(contents[0], username=username, avatar_url=avatar_url,
                                   files=message.attachments if len(message.attachments) > 0 else discord.MISSING,
                                   embed=answer if isinstance(answer, discord.Embed) else discord.MISSING)

    @commands.Cog.listener("on_message_edit")
    async def on_message_edit(self, before: discord.Message, after: discord.Message):
        if before.channel.id == LgChannels.LOUP_CHAT and before.content != after.content:
            if before.author.id in (self.bot.user.id, Users.LUXIO):
                return
            webhook = await get_webhook(self.bot, LgChannels.PETITE_FILLE, "🐺")
            previous_content = before.content
            new_content = after.content
            answer = after.reference
            if answer is not None and (
                    await after.channel.fetch_message(answer.message_id)).author.id != Users.LUXIO:
                answer = await after.channel.fetch_message(answer.message_id)
                answer = discord.Embed(title="En réponse à", description=answer.content)
            if len(new_content) > 2000:
                new_content = new_content[:2000]
            if len(previous_content) > 2000:
                previous_content = previous_content[:2000]
            username = "🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme"
            avatar_url = get_asset("wolf_variant_1") \
                if self.current_pp == 0 else get_asset("wolf_variant_2")
            before_content = discord.Embed(title="Modification du message", description=previous_content)
            await webhook.send(new_content, username=username, avatar_url=avatar_url,
                               files=after.attachments if len(after.attachments) > 0 else discord.MISSING,
                               embeds=[before_content, answer] if isinstance(answer, discord.Embed) else [
                                   before_content])

    @commands.Cog.listener("on_raw_reaction_add")
    async def on_raw_reaction_add(self, payload: discord.RawReactionActionEvent):
        if payload.channel_id == LgChannels.JUGE:
            if payload.emoji.name == "one":
                message = await self.bot.get_channel(payload.channel_id).fetch_message(payload.message_id)
                await message.delete()
            if payload.emoji.name == "two":
                message = await self.bot.get_channel(payload.channel_id).fetch_message(payload.message_id)
                await message.delete()
            if payload.emoji.name == "❌":
                message = await self.bot.get_channel(payload.channel_id).fetch_message(payload.message_id)
                await message.delete()

        if payload.channel_id == LgChannels.LOUP_CHAT and payload.member.id != Users.LUXIO:
            message = await self.bot.get_channel(payload.channel_id).fetch_message(payload.message_id)
            webhook = await get_webhook(self.bot, LgChannels.PETITE_FILLE, "🐺")
            embed = discord.Embed(title="Réaction à un message",
                                  description=message.content if len(message.content) < 1024 else message.content[
                                                                                                  :1021] + "...")
            if message.reference is not None:
                reponse = await message.channel.fetch_message(message.reference.message_id)
                embed.add_field(name="En réponse à",
                                value=reponse.content if len(reponse.content) < 1024 else reponse.content[:1021] + "...")
            await webhook.send(f"Quelqu'un a réagit {payload.emoji} au message ci-dessous", embed=embed,
                               username="🐺Anonyme" if self.current_pp == 0 else "🐺 Anonyme", 
                                avatar_url = get_asset("wolf_variant_1") \
                                if self.current_pp == 0 else get_asset("wolf_variant_2")
                               )
            self.current_pp = 1


def setup(bot):
    bot.add_cog(LG(bot))
