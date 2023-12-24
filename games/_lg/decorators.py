from enums import *
import discord
from discord.ext import commands
import functools

def admin_only():
    async def predicate(ctx: discord.ApplicationContext):
        # Une liste des valeurs d'un Enum
        if ctx.author.guild_permissions.administrator:
            return True
        else:
            await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande.", delete_after=5)
            return False
    return commands.check(predicate)


def check_valid_vote(func):
    @functools.wraps(func)
    async def wrapper(self, ctx: discord.ApplicationContext, member: discord.Member, *args, **kwargs):
        # Vérifie si l'utilisateur vote pour lui-même
        if ctx.author.id == member.id:
            return await ctx.respond("Vous ne pouvez pas voter contre vous même !", delete_after=10)
        # Vérifie si l'utilisateur vote pour un mort
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un mort !", delete_after=10)
        # Vérifie si l'utilisateur vote pour quelqu'un qui n'est pas dans la partie
        if Roles.LG_VIVANT.value not in [role.id for role in member.roles] and Roles.LG_MORT.value not in [role.id for role in member.roles]:
            return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !", delete_after=10)
        # Si toutes les vérifications passent, exécute la commande
        return await func(self, ctx, member, *args, **kwargs)
    return wrapper