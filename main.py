import logging
import os
import sys
from shared import Users, Channels

try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)

load_dotenv()
INTENTS = discord.Intents.all()


class Bot(commands.Bot):
    async def on_ready(self):
        logging.info(f"Logged in as {self.user}")

    async def on_application_command_error(self, ctx: discord.ApplicationContext, error: discord.DiscordException):
        if isinstance(error, commands.CommandOnCooldown):
            await ctx.respond(
                f"Cette commande est en cooldown. Veuillez réessayer dans {error.retry_after:.0f} secondes.",
                ephemeral=True)
        else:
            logging.error(f"Error in {ctx.command}: {error}")
            embed = discord.Embed(title="Une erreur est survenue",
                                  description=f"Erreur provoquée par {ctx.author.mention}",
                                  color=discord.Color.red())
            command = ctx.command
            command_path = []
            while command.parent:
                command_path.append(command.name)
                command = command.parent
            embed.add_field(name="Commande", value=f"`/{''.join(reversed(command_path))}`")
            embed.add_field(name="Module", value=f"`{ctx.command.cog.__class__.__name__!r}`")
            embed.add_field(name="Message d'erreur", value=f"`{error}`")
            embed.add_field(name="Traceback", value=f"```\n{error.__traceback__}```")
            embed.set_footer(text=f"Veuillez transmettre ceci à <@{Users.E_PSI_LON.value}> ou à <@{Users.LUXIO.value}>")
            await ctx.respond(embed=embed, ephemeral=True)

    async def on_error(self, event_method: str, *args, **kwargs) -> None:
        # S'il y a un contexte dans les *args ou dans les **kwargs, on le récupère
        ctx = None
        for arg in args:
            if isinstance(arg, discord.ApplicationContext):
                ctx = arg
                break
        for kwarg in kwargs.values():
            if isinstance(kwarg, discord.ApplicationContext):
                ctx = kwarg
                break
        if ctx:
            logging.error(f"Error in {event_method}: {sys.exc_info()[1]}")
            embed = discord.Embed(title="Une erreur est survenue",
                                  description=f"Erreur provoquée par {ctx.author.mention}",
                                  color=discord.Color.red())
            embed.add_field(name="Module", value=f"`{ctx.command.cog.__class__.__name__!r}`")
            embed.add_field(name="Message d'erreur", value=f"`{sys.exc_info()[1]}`")
            embed.add_field(name="Traceback", value=f"```\n{sys.exc_info()[2]}```")
            embed.set_footer(text=f"Veuillez transmettre ceci à <@{Users.E_PSI_LON.value}> ou à <@{Users.LUXIO.value}>")
            await ctx.respond(embed=embed, ephemeral=True)
        else:
            logging.error(f"Error in {event_method}: {sys.exc_info()[1]}")
            logging.error(f"Traceback: {sys.exc_info()[2]}")
            logging.error(f"Args: {args}")
            logging.error(f"Kwargs: {kwargs}")


bot = Bot(intents=INTENTS)


@bot.listen("on_message")
async def on_message(message: discord.Message):
    if message.channel.id == Channels.IDEES.value:
        await message.create_thread(
            name=message.content if len(message.content) < 100 else message.content[:97] + "...")

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    for file in os.listdir("games"):
        if file.endswith(".py") and file != "__init__.py":
            bot.load_extension(f"games.{file[:-3]}")
            logging.info(f"Loaded game {file[:-3]}")
    bot.run(os.getenv("TOKEN"))
