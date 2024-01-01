import discord
from .enums import *
from .utils import *

class Team(discord.ui.Select):
    def __init__(self, ctx: discord.ApplicationContext, type: str, teams: list, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.options = teams
        self.placeholder = "Quelle équipe voulez-vous attaquer ?" if type == "attack" else "Contre quelle équipe voulez-vous utiliser l'objet/sort ?"
        self.custom_id = f"{type}_team"
        self.ctx = ctx
    
    async def callback(self, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message(f"Vous allez attaquer l'{interaction.guild.get_channel(int(self.values[0])).name} !", ephemeral=True)
        self.view.team = self.values[0]
        # On enlève le select sans supprimer les boutons qui sont déjà là
        self.view.remove_item(self)
        # On active les boutons
        self.view.children[0].disabled = False
        self.view.children[1].disabled = False
        await interaction.message.edit(view=self.view)


class Attack(discord.ui.View):
    def __init__(self, ctx: discord.ApplicationContext, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.ctx = ctx
        teams = list(Teams)
        self.team = None
        teams = [team.value for team in teams]
        options = [discord.SelectOption(label=self.ctx.guild.get_channel(team).name, value=str(team)) for team in teams if team != self.ctx.channel.parent_id]
        self.add_item(Team(ctx, "attack", options, row=0))



    @discord.ui.button(label="Furtivement", style=discord.ButtonStyle.green, custom_id="attack_stealth", disabled=True, row=1)
    async def attack_stealth(self, button: discord.ui.Button, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message("Vous attaquez furtivement.", ephemeral=True)
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "BTW")
        await webhook.send(f"J'attaque l'{interaction.guild.get_channel(int(self.team)).name} furtivement !", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
        await interaction.message.delete()
        

    @discord.ui.button(label="Anonymement", style=discord.ButtonStyle.green, custom_id="attack_anonymously", disabled=True, row=1)
    async def attack_anonymously(self, button: discord.ui.Button, interaction: discord.Interaction):
        if self.ctx.author.id != interaction.user.id:
            await interaction.response.send_message("Vous n'avez pas le droit de faire ça.", ephemeral=True)
            return
        await interaction.response.send_message("Vous attaquez anonymement.", ephemeral=True)
        await interaction.message.edit(view=None)
        webhook = await get_webhook(self.ctx.bot, self.ctx.channel.parent_id, "BTW")
        await webhook.send(f"J'attaque l'{interaction.guild.get_channel(int(self.team)).name} furtivement !", username=interaction.user.display_name, avatar_url=interaction.user.display_avatar.url, thread=self.ctx.channel)
        await interaction.message.delete()
        

class Open(discord.ui.View):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
    

class Use(discord.ui.View):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
    

class Buy(discord.ui.View):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)