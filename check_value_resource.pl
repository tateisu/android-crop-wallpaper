#!usr/bin/perl -w
use strict;
use warnings;
use utf8;
use Encode;
use File::Glob::Windows;
use Text::Diff;

sub getText{
	my($fname) = @_;
	open(my $fh,"<",$fname) or die "$fname $!\n";
	local $/ = undef;
	my $bytes = <$fh>;
	close($fh) or die "$fname $!\n";
	return Encode::decode("utf8",$bytes);
}

my @dirlist = map{ s|\\|/|g;$_} glob( "res/values*/");
my @flist = map{ s|\\|/|g;$_} glob( "res/values/*.xml");
my $count;
for my $file (@flist){
	my $text = getText($file);
	my @a;
	while( $text =~ /<(\S+)\s+name="([^"]+)"/g ){
		push @a,"$1 name=$2\n";
	}
	@a = sort @a;
	$file =~ m|([^/]+)$|;
	my $name = $1;
	for my $dir (@dirlist){
		my $file_b = "$dir/$name";
		next if $file_b eq $file;
		my $text = getText($file_b);
		my @b;
		while( $text =~ /<(\S+)\s+name="([^"]+)"/g ){
			push @b,"$1 name=$2\n";
		}
		@b = sort @b;
		++$count;
		my $diff = diff \@a,\@b,{ CONTEXT=>0,STYLE=>"Unified"};
		next if not $diff;
		print "$file vs $file_b\n";
		print "$diff";
	}
}
warn "compare $count files\n";
