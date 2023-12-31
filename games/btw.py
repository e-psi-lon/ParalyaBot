import discord
from discord.ext import commands
from ._btw import *

class BTW(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
    
    btw = discord.SlashCommandGroup(name="btw", description="Commandes pour le Battery War")

    @btw.command(name="action", description="Faire une action dans le Battery War")
    async def action(self, ctx: discord.ApplicationContext, action: discord.Option(Actions, description="L'action à faire", required=True)):
        if not isinstance(ctx.channel, discord.Thread):
            await ctx.respond("Vous devez utiliser cette commande dans un thread d'équipe.", ephemeral=True)
            return
        match action:
            case Actions.ATTACK:
                await ctx.respond("Attaque", view=Attack(ctx))
            case Actions.OPEN:
                await ctx.respond("Ouverture", view=Open(ctx))
            case Actions.USE:
                await ctx.respond("Utilisation", view=Use(ctx))
            case Actions.BUY:
                await ctx.respond("Achat", view=Buy(ctx))

    
    @btw.command(name="annonce", description="Annoncer un message")
    @admin_only()
    async def annonce(self, ctx, channel: discord.Option(discord.TextChannel, description="Le salon où envoyer l'annonce", required=True), notif: discord.Option(bool, description="Notifier les membres ?", required=False, default=False)):
        await ctx.send_modal(Message(message_callback, channel=channel.id, notif=notif))



def setup(bot):
    bot.add_cog(BTW(bot))