import functools

import discord

from .enums import *


def check_valid_vote(func):
    @functools.wraps(func)
    async def wrapper(self, ctx: discord.ApplicationContext, member: discord.Member, *args, **kwargs):
        # Vérifie si l'utilisateur vote pour un mort
        if (LgRoles.LG_VIVANT not in [role.id for role in member.roles] and LgRoles.LG_MORT in
                [role.id for role in member.roles]):
            return await ctx.respond("Vous ne pouvez pas voter contre un mort !", ephemeral=True)
        # Vérifie si l'utilisateur vote pour quelqu'un qui n'est pas dans la partie
        if (LgRoles.LG_VIVANT not in [role.id for role in member.roles] and LgRoles.LG_MORT not in
                [role.id for role in member.roles]):
            return await ctx.respond("Vous ne pouvez pas voter contre un joueur qui n'est pas dans la partie !",
                                     ephemeral=True)
        # Si toutes les vérifications passent, exécute la commande
        return await func(self, ctx, member, *args, **kwargs)

    return wrapper
