import discord
from discord.ext import commands
from ._btw import *

class BTW(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
    
    btw = discord.SlashCommandGroup(name="btw", description="Commandes pour le Battery War")

    @btw.command(name="action", description="Faire une action dans le Battery War")
    async def action(self, ctx: discord.ApplicationContext, action: discord.Option(str, description="L'action à faire", required=True, choices=[discord.OptionChoice("Attaquer une équipe", "ATTACK"), discord.OptionChoice("Ouvrir une caisse", "OPEN"), discord.OptionChoice("Utiliser un objet/sort de l'inventaire", "USE"), discord.OptionChoice("Acheter un sort à utiliser immédiatement", "BUY")])): 
        if not isinstance(ctx.channel, discord.Thread):
            await ctx.respond("Vous devez utiliser cette commande dans un thread d'équipe.", ephemeral=True)
            return
        match action:
            case "ATTACK":
                await ctx.respond("Attaquer une équipe\nVeuillez sélectionner l'équipe que vous souhaitez attaquer puis le type d'attaque (anonymement  __ou__ furtivement)", view=Attack(ctx))
            case "OPEN":
                await ctx.respond("Ouverture d'une caisse\nVous avez le choix entre une caisse de tier 1 ou de tier 2", view=Open(ctx))
            case "USE":
                await ctx.response.send_modal(Use(ctx))
            case "BUY":
                await ctx.respond("Achat d'un objet/sort à utiliser immédiatement", view=Buy(ctx))

    
    @btw.command(name="annonce", description="Annoncer un message")
    @admin_only()
    async def annonce(self, ctx, channel: discord.Option(discord.TextChannel, description="Le salon où envoyer l'annonce", required=True), notif: discord.Option(bool, description="Notifier les membres ?", required=False, default=False)):
        await ctx.send_modal(Message(message_callback, channel=channel.id, notif=notif))



def setup(bot):
    bot.add_cog(BTW(bot))